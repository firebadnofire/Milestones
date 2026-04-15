# Implementation Notes

## Milestone reset counter

- Add a small text label in the top-right corner of each milestone card showing the reset count for that milestone over the last 7 local days.
- Use a rolling 7-day window, not a calendar week.
- Keep showing the label even when the count is zero.
- When a milestone is reset, continue setting `startDateMillis` to today and also append today to a per-milestone reset history list.
- Persist reset history and include it in import/export.
- Keep backward compatibility with older milestone-only JSON by defaulting missing reset history to an empty list.

## Medicines

- Add a separate `Medicines` screen reachable from the existing navigation drawer.
- Do not mix medicines into the milestone list.
- Support creating a medicine with:
  - a name
  - one or more scheduled times per day
- Track adherence per scheduled dose, not as a single yes/no value for the whole day.
- Store dose logs by local day and scheduled dose index.
- A day counts toward the streak only if every scheduled dose for that day was marked taken.
- If today is incomplete, the current streak should count backward through yesterday.
- Each medicine card should show:
  - the medicine name
  - the current streak
  - today's taken/total summary
  - controls for each scheduled dose today
  - a remove action
- v1 does not need historical backfill or schedule editing. If the schedule changes, the medicine can be removed and recreated.

## Storage and architecture

- Keep the current XML + ViewBinding + RecyclerView structure.
- Keep persistence in `SharedPreferences` using JSON.
- Move persistence to a versioned root app-state payload so milestones and medicines can be stored together.
- Register the medicines screen in the manifest and wire it from the drawer.
- Add unit tests for:
  - rolling 7-day reset counts
  - backward-compatible JSON decoding
  - medicine streak calculations
