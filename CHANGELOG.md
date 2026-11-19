# Changelog

## [0.1.0](https://github.com/508-dev/drums/compare/v0.0.1...v0.1.0) (2026-11-19)


### Features

* add diddle and flam rudiment lessons ([#68](https://github.com/508-dev/drums/issues/68)) ([a4b2b37](https://github.com/508-dev/drums/commit/a4b2b37500d41d8d9f73089bc4b00c761d0e0810)), closes [#21](https://github.com/508-dev/drums/issues/21)
* add drag and timing lessons ([#70](https://github.com/508-dev/drums/issues/70)) ([cc43187](https://github.com/508-dev/drums/commit/cc431879811f4d77465ad56e3b3aa77a759b5dbb)), closes [#22](https://github.com/508-dev/drums/issues/22)
* add library navigation and persistent favorites ([#71](https://github.com/508-dev/drums/issues/71)) ([2bc784b](https://github.com/508-dev/drums/commit/2bc784b2fe6f966f4244eddca7380073fe552959)), closes [#15](https://github.com/508-dev/drums/issues/15)
* add roll rudiment lessons ([#65](https://github.com/508-dev/drums/issues/65)) ([a53666e](https://github.com/508-dev/drums/commit/a53666eade7ff2586c28a9dfaa4ec0ec1a64ef05)), closes [#20](https://github.com/508-dev/drums/issues/20)
* add settings defaults and offline licenses ([#72](https://github.com/508-dev/drums/issues/72)) ([93420ba](https://github.com/508-dev/drums/commit/93420baee80f11bf7f893d8c05b83036cdf2ea1a)), closes [#18](https://github.com/508-dev/drums/issues/18)
* AudioEngine with a sample-exact Kotlin AudioTrack mixer ([#50](https://github.com/508-dev/drums/issues/50)) ([b17a1ad](https://github.com/508-dev/drums/commit/b17a1ad36cdc88c8fbc7352e89fa1abbf647d108)), closes [#6](https://github.com/508-dev/drums/issues/6)
* **audio:** use sampled snare dynamics and buzz rolls ([#66](https://github.com/508-dev/drums/issues/66)) ([f4c6904](https://github.com/508-dev/drums/commit/f4c69041447c44ffb7426cb0cd9dfc2d36d69f84))
* complete practice screen and persist lesson settings ([#69](https://github.com/508-dev/drums/issues/69)) ([10e966a](https://github.com/508-dev/drums/commit/10e966ab6d7e379de499896bee12a0c3955e5ccc)), closes [#16](https://github.com/508-dev/drums/issues/16)
* expand lessons into timed strokes and play them ([#58](https://github.com/508-dev/drums/issues/58)) ([fbc7071](https://github.com/508-dev/drums/commit/fbc70714d8b93852d534d337398a9c44465f831f))
* falling-notes view and the first practice screen ([#61](https://github.com/508-dev/drums/issues/61)) ([893b440](https://github.com/508-dev/drums/commit/893b440a8d39b5a09bb67f4c6f8692ba9e85bd58)), closes [#13](https://github.com/508-dev/drums/issues/13)
* lesson model, MusicXML importer and pack.json loader ([#57](https://github.com/508-dev/drums/issues/57)) ([ba8b778](https://github.com/508-dev/drums/commit/ba8b778931fca81773120ce45903d2ef2bcb5747)), closes [#10](https://github.com/508-dev/drums/issues/10)
* persist practice sessions and show history ([#73](https://github.com/508-dev/drums/issues/73)) ([f391f39](https://github.com/508-dev/drums/commit/f391f396a8bb7c0e4ed010d86a0e0b5e149568f3)), closes [#17](https://github.com/508-dev/drums/issues/17)
* seven-rudiment test set authored in MuseScore ([#59](https://github.com/508-dev/drums/issues/59)) ([892d1c2](https://github.com/508-dev/drums/commit/892d1c2f751fee6ea9cdcdc7407c222be3a9c7ca)), closes [#12](https://github.com/508-dev/drums/issues/12)
* sheet view with a sweeping playhead ([#62](https://github.com/508-dev/drums/issues/62)) ([89cfef9](https://github.com/508-dev/drums/commit/89cfef93f5454dee0523424f5c7abd0594fed1a6)), closes [#14](https://github.com/508-dev/drums/issues/14)
* timing diagnostics screen and M1 measurements on the Sony ([#54](https://github.com/508-dev/drums/issues/54)) ([d7997be](https://github.com/508-dev/drums/commit/d7997be41351d7fece7419196985bcdb6b159a4b)), closes [#8](https://github.com/508-dev/drums/issues/8)
* transport and audio-clock time for the UI ([#53](https://github.com/508-dev/drums/issues/53)) ([5319018](https://github.com/508-dev/drums/commit/5319018a73949ea7f41a399b067012a31f6cd426)), closes [#7](https://github.com/508-dev/drums/issues/7)


### Bug Fixes

* 12 ms grace gap, and a buzz roll that doesn't tick ([#60](https://github.com/508-dev/drums/issues/60)) ([323019b](https://github.com/508-dev/drums/commit/323019b6fc08bf64292a4ad893a9e7233ab75d1f))
* **audio:** recover output tracks without restarting practice ([#74](https://github.com/508-dev/drums/issues/74)) ([ace2631](https://github.com/508-dev/drums/commit/ace2631c9fa32240e1b6000646ecc764bf758a5c)), closes [#51](https://github.com/508-dev/drums/issues/51)
* keep falling-notes grace notes with their main note ([#64](https://github.com/508-dev/drums/issues/64)) ([c065f04](https://github.com/508-dev/drums/commit/c065f042d69c2dfa15b29fb1e58d4764c70e2478)), closes [#63](https://github.com/508-dev/drums/issues/63)
* soft-clip the mix instead of hard-clamping at full scale ([#55](https://github.com/508-dev/drums/issues/55)) ([0babf83](https://github.com/508-dev/drums/commit/0babf83c0fb182f1a012e9ef533f415d5a2fff99))
