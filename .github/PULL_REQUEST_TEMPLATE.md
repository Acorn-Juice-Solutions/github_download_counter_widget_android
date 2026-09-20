<!--
Thanks for the PR. Please tick the checkboxes below (or delete the ones that do not apply)
and give the reviewer just enough context to evaluate the change without opening the diff.
-->

## Summary

<!-- 1-3 sentences. What changes, and why now. -->

## Testing

- [ ] Unit tests updated or added (`./gradlew testDebugUnitTest`)
- [ ] Instrumented tests updated (`./gradlew connectedAndroidTest`) — if the change touches
      the widget provider / worker / Android framework interop

## Quality gates

- [ ] `./gradlew spotlessCheck detekt` passes
- [ ] Screenshots / demo attached if UI changed
- [ ] `CHANGELOG.md` entry under `## [Unreleased]`

## Security

- [ ] No secrets, PATs, or personal repo URLs added to the source tree
- [ ] No new permissions in `AndroidManifest.xml` without justification
