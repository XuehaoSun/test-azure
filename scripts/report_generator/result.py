import hashlib
from typing import Any, Dict, List, Optional

from utils import JsonSerializer


class TuningData(JsonSerializer):
    """Interface for tuning information."""
    def __init__(self) -> None:
        """Initialize TuningData class."""
        super().__init__()
        self.strategy = None
        self.time = None
        self.trials = None
        self.model_size_ratio = None
        self.url = None

class AccuracyData(JsonSerializer):
    """Interface for accuracy information."""
    def __init__(self) -> None:
        """Initialize AccuracyData class."""
        super().__init__()
        self.int8 = {}
        self.fp32 = {}


class PerformanceData(JsonSerializer):
    """Interface for performance information."""
    def __init__(self) -> None:
        """Initialize PerformanceData class."""
        super().__init__()
        self.performance = {}


class Result(JsonSerializer):
    def __init__(self) -> None:
        super().__init__()
        self._platform = None
        self._os = None
        self._python = None
        self._framework = None
        self._version = None
        self._model = None
        self._config_hash = None
        self._tuning = TuningData()
        self._accuracy = AccuracyData()
        self._performance = PerformanceData()

    @property
    def platform(self) -> Optional[str]:
        """Get platform name."""
        return self._platform

    @platform.setter
    def platform(self, value: str) -> None:
        """Set platform name."""
        if isinstance(value, str):
            self._platform = value
            if all(getattr(self, attr) for attr in ["_os", "_platform", "_framework", "_model", "_version"]):
                self._config_hash = hashlib.sha256(
                    "_".join([self.os, self.platform, self._framework, self._model, self._version]).encode('utf-8')
                    ).hexdigest()

    @property
    def os(self) -> Optional[str]:
        """Get OS name."""
        return self._os

    @os.setter
    def os(self, value: str) -> None:
        """Set OS name."""
        if isinstance(value, str):
            self._os = value
            if all(getattr(self, attr) for attr in ["_os", "_platform", "_framework", "_model", "_version"]):
                self._config_hash = hashlib.sha256(
                    "_".join([self.os, self.platform, self._framework, self._model, self._version]).encode('utf-8')
                    ).hexdigest()

    @property
    def python(self) -> Optional[str]:
        """Get python version."""
        return self._python

    @python.setter
    def python(self, value: str) -> None:
        """Set python version."""
        self._python = value

    @property
    def framework(self) -> Optional[str]:
        """Get framework name."""
        return self._framework

    @framework.setter
    def framework(self, value: str) -> None:
        """Set framework name."""
        if isinstance(value, str):
            self._framework = value.lower()
            if all(getattr(self, attr) for attr in ["_os", "_platform", "_framework", "_model", "_version"]):
                self._config_hash = hashlib.sha256(
                    "_".join([self.os, self.platform, self._framework, self._model, self._version]).encode('utf-8')
                    ).hexdigest()

    @property
    def version(self) -> Optional[str]:
        """Get framework version."""
        return self._version

    @version.setter
    def version(self, value: str) -> None:
        """Set framework version."""
        if isinstance(value, str):
            self._version = value.lower()
            if all(getattr(self, attr) for attr in ["_os", "_platform", "_framework", "_model", "_version"]):
                self._config_hash = hashlib.sha256(
                    "_".join([self.os, self.platform, self._framework, self._model, self._version]).encode('utf-8')
                    ).hexdigest()
    
    @property
    def model(self) -> Optional[str]:
        """Get result model."""
        return self._model

    @model.setter
    def model(self, value: str) -> None:
        """Set result model."""
        if isinstance(value, str):
            self._model = value.lower()
            if all(getattr(self, attr) for attr in ["_os", "_platform", "_framework", "_model", "_version"]):
                self._config_hash = hashlib.sha256(
                    "_".join([self.os, self.platform, self._framework, self._model, self._version]).encode('utf-8')
                    ).hexdigest()

    @property
    def config_hash(self) -> Optional[str]:
        """Get configuration hash."""
        return self._config_hash

    @config_hash.setter
    def config_hash(self, value: str) -> None:
        """Set configuration hash."""
        self._config_hash = value

    @property
    def tuning(self) -> Optional[TuningData]:
        """Get result tuning data."""
        return self._tuning

    @property
    def accuracy(self) -> Optional[AccuracyData]:
        """Get result accuracy data."""
        return self._accuracy

    @property
    def performance(self) -> Optional[PerformanceData]:
        """Get result performance data."""
        return self._performance

    def update_tuning_data(self, strategy: str, time: int, trials: int, model_size_ratio: float, url: Optional[str] = None) -> None:
        """Update tuning data in config specified by hash with passed tuning data."""
        self._tuning.strategy = strategy
        self._tuning.time = time
        self._tuning.trials = trials
        self._tuning.model_size_ratio = model_size_ratio
        if url:
            self._tuning.url = url

    def update_perf_data(self, mode: str, precision: str, value: float, url: Optional[str] = None) -> None:
        """Update tuning data in config specified by hash with passed tuning data."""
        update_dict = {precision: {"value": value}}
        if url:
            update_dict[precision].update({"url": url})

        mode_info = getattr(self._performance, mode)
        mode_info.update(update_dict)

    def update_accuracy_data(self, precision: str, value: float, url: Optional[str] = None) -> None:
        """Update tuning data in config specified by hash with passed tuning data."""
        update_data = {"value": value}
        if url:
            update_data.update({"url": url})
        setattr(self._accuracy, precision, update_data)
