from dataclasses import dataclass, field


@dataclass(frozen=True)
class ValidationIssue:
    code: str
    field: str
    message: str


@dataclass
class ValidationResult:
    issues: list[ValidationIssue] = field(default_factory=list)

    @property
    def valid(self) -> bool:
        return not self.issues

    def add(self, code: str, field: str, message: str) -> None:
        self.issues.append(
            ValidationIssue(
                code=code,
                field=field,
                message=message,
            )
        )
