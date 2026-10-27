"""Extract input-to-presentation timings from saved local Perfetto traces."""
import argparse
import csv
import io
import json
from pathlib import Path
import statistics
import subprocess
import sys

QUERY = """
INCLUDE PERFETTO MODULE android.frames.timeline;
WITH actions AS (
  SELECT id, ts, dur, str_split(name, ' ', 1) sample FROM slice
  WHERE name GLOB 'SendItAction *'
), native_inputs AS (
  SELECT ts, dur, track_id, name label FROM slice WHERE name GLOB 'dispatchInputEvent *'
  UNION ALL
  SELECT i.ts, i.dur, i.track_id, substr(m.name, 13) || ' ' || i.name
  FROM slice m JOIN slice i ON i.ts BETWEEN m.ts AND m.ts+m.dur
  JOIN thread_track mt ON mt.id=m.track_id JOIN thread marker_thread ON marker_thread.utid=mt.utid
  JOIN thread_track it ON it.id=i.track_id JOIN thread input_thread ON input_thread.utid=it.utid
  WHERE m.name GLOB 'SendItInput *' AND i.name GLOB 'deliverInputEvent src=*'
    AND marker_thread.upid=input_thread.upid
), feedback AS (
  SELECT s.id, s.ts, str_split(s.name, ' ', 1) sample, str_split(s.name, ' ', 2) window_id,
         f.frame_id, t.upid
  FROM slice s JOIN ancestor_slice(s.id) a
  JOIN android_frames_choreographer_do_frame f ON f.id=a.id
  JOIN thread_track tt ON tt.id=s.track_id JOIN thread t USING(utid)
  WHERE s.name GLOB 'SendItFeedback *'
)
SELECT 'input' kind, a.sample, i.ts, i.ts+i.dur end_ts, NULL frame_id,
       NULL layer, NULL window_id, i.label
FROM actions a JOIN native_inputs i ON i.ts BETWEEN a.ts AND a.ts+a.dur
JOIN thread_track it ON it.id=i.track_id JOIN thread ith USING(utid)
JOIN thread_track at ON at.id=(SELECT track_id FROM slice WHERE id=a.id)
JOIN thread ath ON ath.utid=at.utid
WHERE ith.upid=ath.upid
UNION ALL
SELECT 'accepted', str_split(name, ' ', 1), ts, ts+dur, NULL, NULL, NULL, name FROM slice
WHERE name GLOB 'SendItAccepted *'
UNION ALL
SELECT 'feedback', d.sample, d.ts, sf.ts+sf.dur, d.frame_id,
       app.layer_name, d.window_id, app.present_type
FROM feedback d
LEFT JOIN actual_frame_timeline_slice app ON app.surface_frame_token=d.frame_id AND app.upid=d.upid
LEFT JOIN actual_frame_timeline_slice sf ON sf.display_frame_token=app.display_frame_token
    AND sf.surface_frame_token IS NULL
UNION ALL
SELECT 'health', '', value, NULL, NULL, NULL, NULL, name FROM stats
WHERE value>0 AND severity IN ('error','data_loss')
  AND name != 'config_write_into_file_no_flush';
"""

def extract(trace, samples, processor):
    result = subprocess.run([sys.executable, str(processor), '--full-sort', 'query', str(trace), QUERY],
                            capture_output=True, text=True, check=True)
    raw = list(csv.DictReader(io.StringIO(result.stdout)))
    trace.with_suffix('.query.csv').write_text(result.stdout)
    health = [r['label'] for r in raw if r['kind'] == 'health']
    rows = []
    for sample in samples:
        row = dict(sample, trace=trace.name, duration_ms=None, timing_validated=False)
        try:
            assert not health, 'Trace health errors: ' + ', '.join(health)
            assert sample['outcome'] == 'draw_observed', sample.get('error')
            submitted = sample.get('input') == 'submission'
            events = [r for r in raw if r['sample'] == sample['sample'] and r['kind'] == ('accepted' if submitted else 'input')]
            expected = ('SendItAccepted' if submitted else
                        'KeyEvent ACTION_DOWN' if sample.get('input') == 'key' else 'MotionEvent ACTION_UP')
            inputs = [r for r in events if expected in r['label']]
            # Newer Android emits both stages; retain its original outer dispatch boundary.
            inputs = [r for r in inputs if r['label'].startswith('dispatchInputEvent ')] or inputs
            assert len(inputs) == 1, f'Expected one native {expected} delivery, found {len(inputs)}'
            start = int(inputs[0]['ts'])
            titles = {str(w['id']): w['title'] for w in sample['windows']}
            candidates = [r for r in raw if r['sample'] == sample['sample'] and r['kind'] == 'feedback'
                        and titles[r['window_id']] in r['layer'] and int(r['ts']) >= start]
            feedback = [r for r in candidates if r['label'] != 'Dropped Frame' and r['end_ts'] != '[NULL]']
            assert feedback, 'No presented frame matched the expected-feedback draw'
            first = min(feedback, key=lambda r: int(r['end_ts']))
            first_draw = [r for r in candidates if r['ts'] == first['ts'] and r['window_id'] == first['window_id']]
            # A shared title is harmless only when every possible layer was presented at exactly the same time.
            assert all(r['label'] != 'Dropped Frame' and r['end_ts'] == first['end_ts'] for r in first_draw), \
                'Ambiguous target-window presentation time in the first response frame'
            layers = sorted({r['layer'] for r in first_draw})
            duration = (int(first['end_ts']) - start) / 1_000_000
            assert duration >= 0, 'Presentation precedes input'
            row.update(duration_ms=duration, timing_validated=True, input_ts=start,
                       present_ts=int(first['end_ts']), frame_id=first['frame_id'],
                       layer=layers[0] if len(layers) == 1 else None, candidate_layers=layers,
                       input_boundary=expected, outcome='measured', error=None)
        except (AssertionError, KeyError, ValueError) as error:
            row.update(outcome='unmeasured', error=str(error))
        rows.append(row)
    return rows

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('results', type=Path)
    parser.add_argument('--trace-processor', type=Path, required=True)
    args = parser.parse_args()
    rows = []
    for samples_path in sorted((args.results / 'device').glob('*.samples.jsonl')):
        samples = [json.loads(line) for line in samples_path.read_text().splitlines()]
        trace = samples_path.with_name(samples_path.name.replace('.samples.jsonl', '.pftrace'))
        if trace.exists():
            rows.extend(extract(trace, samples, args.trace_processor))
        else:
            rows.extend(dict(sample, duration_ms=None, timing_validated=False, outcome='unmeasured', error='Trace missing') for sample in samples)
    (args.results / 'timings.jsonl').write_text(''.join(json.dumps(r) + '\n' for r in rows))
    calibration = {r['sample']: r for r in rows if r['sample'].startswith('calibration_')}
    valid = (len(calibration) == 2 and all(r['timing_validated'] for r in calibration.values())
             and calibration['calibration_0']['duration_ms'] < 200
             and calibration['calibration_250']['duration_ms'] >= 250)
    plan = json.loads((args.results / 'build.json').read_text())
    observations = [json.loads(line) for line in (args.results / 'device/evaluation.jsonl').read_text().splitlines()]
    summary = {'calibration_passed': valid, 'processing': [], 'interactions': []}
    for kind, fields, limit in [('processing', ['device', 'video'], 120000),
                                 ('interaction', ['device', 'action', 'condition'], 200)]:
        groups = {}
        for row in rows:
            if row.get('kind') == kind:
                groups.setdefault(tuple(row.get(f) for f in fields), []).append(row)
        for key, group in groups.items():
            measured = [r['duration_ms'] for r in group if r['timing_validated']]
            summary['processing' if kind == 'processing' else 'interactions'].append(dict(zip(fields, key),
                samples=len(group), measured=len(measured),
                median_ms=statistics.median(measured) if measured else None,
                max_ms=max(measured) if measured else None,
                threshold_violations=sum(t >= limit if kind == 'processing' else t > limit for t in measured)))
    summary['outcomes'] = {kind: {outcome: sum(r['kind'] == kind and r['outcome'] == outcome for r in observations)
                               for outcome in sorted({r['outcome'] for r in observations if r['kind'] == kind})}
                           for kind in sorted({r['kind'] for r in observations})}
    summary['storage'] = [r for r in observations if r['kind'] == 'storage']
    summary['not_applicable'] = [r for r in observations if r['outcome'] == 'not_applicable']
    summary['checks'] = {}
    fixtures = [r for r in observations if r['kind'] == 'fixture']
    if fixtures:
        summary['checks']['fixture_checksums'] = all(r['source_sha256'] ==
            plan['fixtures'].get(r['video'].removeprefix('sendit-eval-')) for r in fixtures)
    methods = plan.get('methods', [])
    for method in methods:
        output = args.results / (method + '.txt')
        native = [r for r in observations if r['kind'] == 'test_method' and r.get('method') == method]
        summary['checks'][method] = (len(native) == 1 and native[0]['outcome'] == 'pass') if native else (
            output.exists() and 'OK (' in output.read_text())
    if 'aProcessing' in methods:
        for video in plan['videos']:
            group = [r for r in rows if r.get('kind') == 'processing' and r.get('video') == video and r['timing_validated']]
            summary['checks']['processing_' + video] = (len(group) == plan['video_repetitions'] and
                {r['repetition'] for r in group} == set(range(1, plan['video_repetitions'] + 1)))
    if 'cInteractions' in methods:
        actions = ('open_route select_route type_route_name open_grade select_grade open_date select_date '
                   'confirm_date select_outcome type_location type_notes back_to_form').split()
        for action in actions:
            group = [r for r in rows if r.get('action') == action and r.get('condition') == 'idle' and r['timing_validated']]
            summary['checks'][action] = (len(group) == plan['interaction_repetitions'] and
                {r['repetition'] for r in group} == set(range(1, plan['interaction_repetitions'] + 1)))
        summary['checks']['processing_applicability'] = len(summary['not_applicable']) == len(actions)
    if any(m in methods for m in ('aProcessing', 'cInteractions', 'zLatencyCalibration')):
        summary['checks']['native_timing_calibration'] = valid
    if 'eFootprint' in methods:
        summary['checks']['storage_checkpoints'] = {r['repetition'] for r in summary['storage']} == {1, 5, 10}
    summary['evidence_complete'] = bool(summary['checks']) and all(summary['checks'].values())
    summary['thresholds_passed'] = (summary['evidence_complete'] and
        all(g['threshold_violations'] == 0 for g in summary['processing'] + summary['interactions']))
    (args.results / 'summary.json').write_text(json.dumps(summary, indent=2))
    print(json.dumps(summary, indent=2))
    if not summary['evidence_complete']:
        raise SystemExit('Evaluation evidence is incomplete; see summary.json')
    if not summary['thresholds_passed']:
        raise SystemExit('Evaluation collected; performance thresholds were exceeded')

if __name__ == '__main__':
    main()
