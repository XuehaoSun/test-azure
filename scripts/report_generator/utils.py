import re
from typing import Any, Dict, List, Union


class JsonSerializer:
    """Dict serializabe class."""

    def __init__(self) -> None:
        """Initialize json serializable class."""
        # List of variable names that will
        # be skipped during serialization
        self._skip = ["_skip"]

    def serialize(self, serialization_type: str = "default") -> Union[Dict[str, Any], List[Dict[str, Any]]]:
        """
        Serialize class to dict.

        :param serialization_type: serialization type, defaults to "default"
        :type serialization_type: str, optional
        :return: serialized class
        :rtype: Union[dict, List[dict]]
        """
        result = {}
        for key, value in self.__dict__.items():
            if key in self._skip:
                continue

            variable_name = re.sub(r"^_", "", key)
            if issubclass(type(value), JsonSerializer):
                result[variable_name] = value.serialize(serialization_type)
            elif issubclass(type(value), list):
                    serialized_array = []
                    for item in value:
                        serialized_array.append(item.serialize())
                    result[variable_name] = serialized_array
            else:
                result[variable_name] = self.serialize_item(value)

        return result

    def serialize_item(self, value: Any) -> Any:
        """
        Serialize objects that don't support json dump.

        i.e datetime object can't be serialized to JSON format and throw an TypeError exception
        TypeError: datetime.datetime(2016, 4, 8, 11, 22, 3, 84913) is not JSON serializable
        To handle that override method serialize_item to convert object
            >>> serialize_item(datetime)
            "2016-04-08T11:22:03.084913"

        For all other cases it should return serializable object i.e. str, int float

        :param value: Any type
        :return: Value that can be handled by json.dump
        """
        return value
