#!/usr/bin/env python3
"""Synthetic parser tests only. These are not device benchmark results."""
import copy
import csv
import importlib.util
import subprocess
import sys
import tempfile
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("report", Path(__file__).with_name("benchmark_report.py"))
report = importlib.util.module_from_spec(spec)
spec.loader.exec_module(report)


def rows():
    base = dict(device="Synthetic parser fixture", android="14", sdk="34", source_revision="a" * 40,
                error="", ttff_ms="100", seek_ms="20", dropped_frames="0", cpu_percent_one_core="10",
                cpu_ms="1000", wall_ms="10000", playing="true", position_ms="1000", build_fingerprint="synthetic/os/build",
                active_audio="0", active_video="0", wake_locks="0", elapsed_s="1800", pss_kb="100", fd_count="40",
                sha256="b" * 64, size_bytes="12345", duration_s="15", status="pass")
    refs = [dict(base, kind="reference", file="h264.mp4", codec="h264", width="1920", height="1080", bit_depth="8", transfer="bt709"),
            dict(base, kind="reference", file="hevc.mp4", codec="hevc", width="3840", height="2160", bit_depth="8", transfer="bt709"),
            dict(base, kind="reference", file="hdr.mp4", codec="hevc", width="1920", height="1080", bit_depth="10", transfer="smpte2084")]
    samples = [dict(base, kind="background_sample", elapsed_s=str(t), wall_ms=str(t * 1000 + 1000),
                    cpu_ms=str(t * 100), position_ms=str((t * 1000) % 15000)) for t in range(10, 1801, 10)]
    return refs + samples + [dict(base, kind="background_release", elapsed_s="1811")]


class ReportTest(unittest.TestCase):
    def test_complete_gate(self):
        text, passed = report.summarize(rows())
        self.assertTrue(passed)
        self.assertIn("Synthetic parser fixture", text)

    def test_missing_reference(self):
        self.assertFalse(report.summarize(rows()[1:])[1])

    def test_unknown_wake_locks(self):
        data = rows(); data[-1]["wake_locks"] = "unknown"; data[-1]["status"] = "inconclusive"
        self.assertFalse(report.summarize(data)[1])

    def test_short_cannot_pass_even_when_label_is_pass(self):
        data = rows(); data[-1]["elapsed_s"] = "30"
        self.assertFalse(report.summarize(data)[1])

    def test_negative_timing_or_failure_cannot_pass(self):
        data = rows(); data[0]["ttff_ms"] = "-1"
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[-2]["status"] = "fail"
        self.assertFalse(report.summarize(data)[1])

    def test_no_hdr_or_4k_cannot_pass(self):
        data = rows(); data[2]["bit_depth"] = "8"
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[1]["width"] = "1920"
        self.assertFalse(report.summarize(data)[1])

    def test_missing_or_mixed_revision_rejected(self):
        data = rows(); data[0]["source_revision"] = "local-unknown"
        with self.assertRaises(ValueError): report.summarize(data)
        data = rows()
        for row in data: row["source_revision"] = "local-unknown"
        with self.assertRaises(ValueError): report.summarize(data)

    def test_partial_run_no_release(self):
        self.assertFalse(report.summarize(rows()[:-1])[1])

    def test_single_late_sample_does_not_prove_a_long_run(self):
        data = rows()
        self.assertFalse(report.summarize(data[:3] + data[-2:])[1])

    def test_gap_duplicate_and_out_of_order_samples_rejected(self):
        for mode in ("gap", "duplicate", "reverse"):
            with self.subTest(mode=mode):
                data = rows()
                if mode == "gap": del data[12:20]
                elif mode == "duplicate": data[12]["elapsed_s"] = data[11]["elapsed_s"]
                else: data[11], data[12] = data[12], data[11]
                self.assertFalse(report.summarize(data)[1])

    def test_nonfinite_or_missing_metrics_never_pass(self):
        for key in ("ttff_ms", "seek_ms", "cpu_percent_one_core", "cpu_ms", "wall_ms", "dropped_frames"):
            for bad in ("nan", "inf", "-1", "", "not measured"):
                with self.subTest(key=key, bad=bad):
                    data = rows(); data[0][key] = bad
                    self.assertFalse(report.summarize(data)[1])

    def test_background_requires_playback_progress_and_cpu(self):
        for key, value in (("playing", "false"), ("position_ms", ""), ("cpu_ms", "nan"), ("wall_ms", "0")):
            with self.subTest(key=key):
                data = rows(); data[12][key] = value
                self.assertFalse(report.summarize(data)[1])
        data = rows(); data[12]["position_ms"] = data[11]["position_ms"]
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[12]["cpu_ms"] = "0"
        self.assertFalse(report.summarize(data)[1])

    def test_missing_hash_and_bad_counts_rejected(self):
        data = rows(); data[0]["sha256"] = "unknown"
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[0]["dropped_frames"] = "0.5"
        self.assertFalse(report.summarize(data)[1])

    def test_release_must_follow_samples(self):
        for when in ("1799", "1900"):
            data = rows(); data[-1]["elapsed_s"] = when
            self.assertFalse(report.summarize(data)[1])

    def test_unknown_memory_is_not_formatted_as_a_valid_range(self):
        data = rows(); data[10]["pss_kb"] = "unknown"
        text, _ = report.summarize(data)
        self.assertIn("PSS KB: **incomplete / unavailable**", text)

    def test_matching_baseline_shows_numeric_changes(self):
        baseline = rows(); current = copy.deepcopy(baseline)
        for r in current: r["source_revision"] = "c" * 40
        current[0]["ttff_ms"] = "120"
        text, passed = report.summarize(current, baseline)
        self.assertTrue(passed)
        self.assertIn("+20.0%", text)
        self.assertIn("single observations", text)

    def test_comparison_zero_baseline_is_explicit(self):
        baseline = rows(); baseline[0]["ttff_ms"] = "0"
        text, passed = report.summarize(rows(), baseline)
        self.assertTrue(passed)
        self.assertIn("zero baseline", text)

    def test_comparison_refuses_different_device_os_or_bytes(self):
        for key in ("device", "android", "build_fingerprint", "sha256", "duration_s"):
            with self.subTest(key=key):
                baseline = rows()
                for row in baseline: row[key] = "different"
                text, passed = report.summarize(rows(), baseline)
                self.assertFalse(passed)
                self.assertIn("comparison **inconclusive**", text)

    def test_comparison_refuses_duplicate_or_failed_reference(self):
        for mode in ("duplicate", "fail"):
            baseline = rows()
            if mode == "duplicate": baseline[1]["file"] = baseline[0]["file"]
            else: baseline[0]["status"] = "fail"
            self.assertFalse(report.summarize(rows(), baseline)[1])

    def test_missing_device_identity_rejected(self):
        data = rows()
        for row in data: row["device"] = ""
        with self.assertRaises(ValueError): report.summarize(data)

    def test_cli_preserves_partial_evidence_and_surrounding_document(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data = rows()[:3]
            source = root / "partial.csv"
            keys = sorted({key for row in data for key in row})
            with source.open("w", newline="") as output:
                writer = csv.DictWriter(output, fieldnames=keys); writer.writeheader(); writer.writerows(data)
            target = root / "report.md"
            target.write_text("Intro\n" + report.START + "\nPending\n" + report.END + "\nFootnote\n")
            result = subprocess.run([sys.executable, str(Path(report.__file__)), str(source), "--output", str(target)], capture_output=True, text=True)
            self.assertNotEqual(0, result.returncode)
            text = target.read_text()
            self.assertTrue(text.startswith("Intro\n")); self.assertTrue(text.endswith("Footnote\n"))
            self.assertIn("PENDING / FAILED / INCONCLUSIVE", text)
            self.assertIn("h264.mp4", text)

    def test_unrecognized_rows_or_errors_prevent_acceptance(self):
        data = rows(); data.append(dict(data[0], kind="unexpected"))
        self.assertFalse(report.summarize(data)[1])
        data = rows(); data[-1]["error"] = "late failure"
        self.assertFalse(report.summarize(data)[1])

    def test_cli_matched_baseline_is_written_without_touching_baseline(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            data = rows()
            keys = sorted({key for row in data for key in row})
            source = root / "current.csv"; baseline = root / "baseline.csv"
            for path in (source, baseline):
                with path.open("w", newline="") as output:
                    writer = csv.DictWriter(output, fieldnames=keys); writer.writeheader(); writer.writerows(data)
            before = baseline.read_bytes()
            target = root / "report.md"
            target.write_text(report.START + "\nPending\n" + report.END)
            result = subprocess.run([sys.executable, str(Path(report.__file__)), str(source), "--output", str(target), "--baseline", str(baseline)], capture_output=True, text=True)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("Matched timing comparison", target.read_text())
            self.assertEqual(before, baseline.read_bytes())

    def test_empty_rejected(self):
        with self.assertRaises(ValueError): report.summarize([])


if __name__ == "__main__":
    unittest.main()
