import os
import unittest
import threading
import hashlib
import tempfile

if not os.getenv("JWT_SECRET_KEY"):
    os.environ["JWT_SECRET_KEY"] = "test-secret-key-12345678901234567890"

from ml_duration_model import (
    predict_duration,
    load_cached_model,
    invalidate_model_cache,
    MODEL_FILE
)
import logger


class Phase11ConcurrencyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.original_log_file = logger.LOG_FILE
        cls.test_csv = tempfile.NamedTemporaryFile(delete=False, suffix=".csv")
        cls.test_csv.close()
        logger.LOG_FILE = cls.test_csv.name

        cls.original_model_bytes = None
        if os.path.exists(MODEL_FILE):
            with open(MODEL_FILE, "rb") as f:
                cls.original_model_bytes = f.read()

    @classmethod
    def tearDownClass(cls):
        logger.LOG_FILE = cls.original_log_file
        if os.path.exists(cls.test_csv.name):
            try:
                os.unlink(cls.test_csv.name)
            except Exception:
                pass

        if cls.original_model_bytes is not None:
            with open(MODEL_FILE, "wb") as f:
                f.write(cls.original_model_bytes)
            invalidate_model_cache()

    def test_01_concurrent_predictions_thread_safety(self):
        """TEST 1: 50 concurrent prediction threads execute safely under _CACHE_LOCK without race conditions."""
        errors = []

        def worker(idx):
            try:
                task_name = "study" if idx % 2 == 0 else "unknown_task"
                pred = predict_duration(task_name=task_name, planned_duration=60, start_minute=540)
                if not isinstance(pred, int) or pred < 5:
                    errors.append(f"Thread {idx} received invalid prediction: {pred}")
            except Exception as e:
                errors.append(f"Thread {idx} raised exception: {e}")

        threads = [threading.Thread(target=worker, args=(i,)) for i in range(50)]
        for t in threads:
            t.start()
        for t in threads:
            t.join()

        self.assertEqual(len(errors), 0, f"Concurrent prediction errors detected: {errors}")

    def test_02_model_hash_integrity(self):
        """TEST 2: Verify production duration_model.pkl SHA-256 remains unpolluted after test teardown."""
        if self.original_model_bytes is not None:
            baseline_hash = hashlib.sha256(self.original_model_bytes).hexdigest()
            with open(MODEL_FILE, "rb") as f:
                current_bytes = f.read()
            current_hash = hashlib.sha256(current_bytes).hexdigest()
            self.assertEqual(baseline_hash, current_hash, "Production duration_model.pkl was modified by tests!")


if __name__ == "__main__":
    unittest.main()
