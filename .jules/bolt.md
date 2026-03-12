## 2024-03-12 - DOM Rendering Reflows
**Learning:** Found an anti-pattern of appending individual DOM elements repeatedly in loops (e.g. `grid.appendChild(card)`) during large UI renders like `generateScoreCards`, `generateStandardsTable`, and `generateDistributionCards`. This causes multiple reflows/repaints and noticeably impacts rendering speed on heavy data sets like grades and statistics.
**Action:** Use `DocumentFragment` to batch construct elements off-DOM, then perform a single `appendChild` to apply them to the live container, reducing reflows from O(N) to O(1).
