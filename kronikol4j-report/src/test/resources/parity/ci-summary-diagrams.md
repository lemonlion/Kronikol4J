# Diagrammed Test Run Summary

| Metric | Value |
|---|---|
| Status | ✅ Passed |
| Scenarios | 1 |
| Passed | 1 |
| Failed | 0 |
| Skipped | 0 |
| Duration | 1m 5s |

## Sequence Diagrams

<details><summary>✅ <strong>Checkout — Places an order</strong></summary>

![diagram](https://plantuml.com/plantuml/svg/UhzxlqDnIM9HIMbk3bT8Qd69WgwTGdvHIcfHS6fHMMPogfL2W7zmY9L2Hab9WPgogOMrldvnMR8-M4an5x9A1LrTEmMG4LOAHWQ6N0wfUIcbkJa00000__y30000)

<details><summary>Sequence Diagram - PlantUML</summary>

```plantuml
@startuml
Test -> orderService: POST: http://svc/orders
orderService --> Test: 201
@enduml
```

</details>

</details>

