# Daily Sudoku

One Sudoku a day for the Light Phone III, and then it's done until tomorrow.

- The same puzzle for everyone on a given day, generated on the phone from the date. Easy on Monday, hardest on Sunday. Every puzzle has exactly one solution.
- Notes mode for pencil marks, erase, and up to 3 undos per game. Time and streak are shown only once you've solved it.

## Privacy

No account, no tracking, no analytics, no ads, and no network access: the tool requests no permissions. Puzzles are generated on the phone, and your progress, streak and best time stay on the phone.

## License

MIT, see `LICENSE`.

## Build

This repo holds only the `tool/` module. Copy it into a checkout of [light-sdk](https://github.com/lightphone/light-sdk), replacing its `tool/`, then:

```sh
./gradlew :tool:testDebugUnitTest :tool:assembleRelease
```
