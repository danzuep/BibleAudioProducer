# Bible Audio Producer

Record and review target-language audio associated with Greek forms using the
[MACULA Greek](https://github.com/Clear-Bible/macula-greek/) and
[MACULA Hebrew](https://github.com/Clear-Bible/macula-hebrew/) linguistic datasets.
Greek New Testament support is included first; Hebrew is not yet bundled.
The complete Greek New Testament recording plan is bundled and available offline on first
launch. The first screen lists projects; a new project with no name is assigned a name such
as **Project001**. New projects start with John 3:16 to try a word order and record a
verse; existing projects keep their previous behavior. Select a book, tap the chapter
number to choose an available chapter with the number picker, then choose **Show verses**
to navigate its verses. Changing books resets the chapter to 1 (or the first available chapter). Back
returns to the previous screen. Tap a word to
inspect its Macula metadata and English gloss, then record or play its target-language equivalent.
Hold a word until it lifts, drag it into place, and release to drop it. Reset restores the
original order with the **Original order** preset. Disable or restore a form from its word details.
Closing the verse returns to the chapter list or recording cards. Select a book and chapter side by side;
cards are sorted by frequency within that chapter. Open **Settings** from the gear
icon to set the recording language, import plans or audio, export archives, or manage data.
Imports in a different language are rejected to prevent mixing projects. Plans and
recordings remain in private storage; export them before uninstalling.

In a verse, tap the record button beside playback to open the recording editor, then
select a word-order mode or drag individual script words. Record the enabled words' translations in the
displayed card order; omit disabled words. Choose an English, Chinese, original Greek,
transliteration, or custom recording guide. The app suggests boundaries at quiet points in
recording order and shows them on a chronological waveform above the card deck. A live
microphone waveform appears during recording. Tap a slice or card to select it;
drag its waveform handles or use the 10 ms nudge buttons. The nudge
buttons move the selected slice's end boundary
(or its start for the last slice). Trim sliders remove milliseconds from the selected
clip's start and end without moving shared boundaries. The app suggests skipping leading
silence while keeping the original take; adjust the first start handle to restore audio if needed.
The recording end is fixed. The waveform initially shows one second around the first word;
zoom follows the selected slice. Adjacent slices share their boundary: moving it
changes both clips and requires reviewing both again. Cards always follow audio order.
Drag a card's handle to change its position, or drop the card onto a waveform segment.
Moving a card shifts the intervening cards without changing boundaries; every affected clip must
be reviewed again. Previously saved clips remain unchanged until saved again.
Preview each clip and use **Next** or its check mark to save and advance, or **Keep & next**
to keep its existing clip. A green check mark indicates a saved recording.
The Android editor currently maps one spoken slice to each enabled Greek form; editable
card text, split/merge, unassigned blobs and alignment ZIP export
remain available only in the separate browser slicer below.
Suggestions assume one spoken segment per enabled card; translations with extra, missing,
or combined words need manual adjustment. They are not speech recognition and must be
reviewed. Verse clips are saved as lossless WAV audio with
short fades, including very short selections.
Each saved clip is keyed by Greek surface form, so repeated forms share a
recording: replacing any existing clip requires confirmation. Back
closes the editor and discards the unsaved verse take after confirmation, but keeps clips already saved.
Once every word is reviewed, **Finish verse**
saves the order and advances from John 3:16 to John 1:1, then through the following
verses. Selecting a preset in John 3:16 saves it immediately as this project's default,
even without recording. Original order and Custom do not replace that default.
Subsequent verses use it for the editor, recorded playback and export unless they
already have a saved order; a custom order is specific to its verse.
The recorder's script includes the word-order and guide-language selectors; drag script
words to reorder them or tap one for its details. Changing enabled words requires
discarding the current take first.

Word-order presets are starting points, not translations. The bundled Greek data uses
[MACULA's pinned lowfat XML syntax trees](https://github.com/Clear-Bible/macula-greek/tree/8423afe47b9e8f24b7772e808af45c7159a6fe7e/SBLGNT/lowfat)
directly to keep coordinated phrases and nested clauses together and distinguish subjects
from predicates. The bundled recording plan is generated from the same XML words.
Recording keys now use XML surface forms, so recordings saved under the former
spellings of seven forms will need to be recorded again.
For example, the English preset uses the ordering relationships in the
[Berean Standard Bible, John 1:1 and 3:16](https://github.com/usfm-bible/examples.bsb/blob/50a493b1a6a7828f15833f95920bed81c69fb710/44JHNBSB.usfm)
as a grammar reference, not as a word-for-word target. Translations add, omit and
change words, so they cannot generally be obtained by permuting Greek tokens.
All presets benefit from the same phrase and clause boundaries, but their orders are
approximations, not full grammars of English, German, Mandarin, Japanese, Korean,
Arabic or Hebrew.
German uses a fronted constituent followed by the finite verb in main clauses,
and approximates verb-final order in subordinate clauses; it cannot add auxiliaries.
The [Freie Lutherbibel's John 3:16](https://github.com/bibliadelpueblo/BibliaLibre/blob/bed6a5c56de7db4895304553c6d7c3e763480f10/Bibles/German.FreieLuther/43-Johannes.md)
is an ordering reference, not a word-for-word target.
The other presets were compared with John 3:16 in the eBible-derived
[Chinese Union Version](https://github.com/BibleNLP/ebible/blob/2bde52b4c2cfd7056cf4ed07c48969647df79744/corpus/cmn-cmn_cu89s.txt),
[Japanese New Japanese NT](https://github.com/BibleNLP/ebible/blob/2bde52b4c2cfd7056cf4ed07c48969647df79744/corpus/jpn-jpn1965.txt),
[Arabic Van Dyck](https://github.com/BibleNLP/ebible/blob/2bde52b4c2cfd7056cf4ed07c48969647df79744/corpus/arb-arb_vd.txt)
and [Modern Hebrew Bible](https://github.com/BibleNLP/ebible/blob/2bde52b4c2cfd7056cf4ed07c48969647df79744/corpus/heb-heb.txt),
and the separately mirrored [Korean Bible (1910)](https://github.com/Waeil55/bible/blob/efe16c5cba21a919732d2784062de295c8628df5/data/bible/langs/index.json).
The first four are in the pinned [eBible catalog snapshot](https://github.com/BibleNLP/ebible/blob/2bde52b4c2cfd7056cf4ed07c48969647df79744/metadata/translations.csv);
the Korean reference is not in that snapshot. Japanese/Korean and Arabic/Hebrew
each share a rough preset, not a language-specific grammar. Adverbial clauses retain
their position relative to surrounding constituents, while whole object clauses can
move; a translation may instead restructure or split those clauses.
Japanese postpositions are approximated only for intact prepositional phrases.
English, Mandarin and Japanese move post-head modifiers in explicitly headed noun
phrases; German also moves these modifiers before nouns. For the bundled data, which
has no explicit head annotations, these modes also handle MACULA's noun-adjective and
noun-article-adjective rules. Both Greek articles are retained if present, with each
kept alongside its noun or adjective; omit or adapt them when translating.
Other noun modifiers, negation, implicit subjects, discourse and idioms require
human review. Where syntax is missing, malformed, crosses verse boundaries,
is discontinuous or ambiguous, the
affected words retain their source order. Greek and Custom remain unchanged. Saved
and manually selected orders still take precedence; drag words to correct a preset.

The chapter verse list and verse text show pronunciation above each Greek word and
English and Chinese glosses below where available. Recorded forms are marked green and
disabled forms grey. Verse and chapter playback use target-language recordings where
available and the selected Android modern Greek TTS voice for missing words. This is
Greek source text, not a translation, and pronunciation depends on the voice. Choose an
installed Greek voice in Settings; the voice provider may process speech locally or over
its network. On Samsung, set the engine owning the downloaded voice as the preferred
engine in Android's **Text-to-speech output** settings, then reopen the app and choose
the voice in **Settings**.

Inspired by BTT Recorder's take-review workflow, replacing an existing recording requires
reviewing the new take first. The current take remains intact until the replacement is
accepted, including if the app is interrupted during review.

In verse word details, tap Record to open the take dialog immediately. The live new
waveform appears above the current take, if there is one. Stop recording to preview the
takes with their play/stop buttons, then select a waveform to keep that take and close
the dialog. Closing a playback window stops its audio.

On recording cards, hold the red record button and release to save; drag far away or
cancel the gesture to discard the take. A live waveform shows microphone activity. When
using an accessibility service or keyboard, activate the button once to start and again
to stop. After granting microphone permission, start the recording again. The play
button changes to stop during playback. Disabled forms are skipped in playback and shown
in grey; recordings and exported archives are keyed by the Greek surface form rather than
shared by the lemma.
When a word-list recording is saved, the next unrecorded card is brought to the same
position in the list.

Playback normalizes levels, trims silence, and crossfades joins. Choose **Save verse
audio** to export in the selected order as lossless WAV, compatible AAC (M4A), or compact
Opus (Ogg, Android 10 or later). When words lack recordings, the download dialog warns
that modern Greek TTS will be included and offers a checkbox to strip it instead.
If stripping leaves no recordings, the download cannot proceed. Very short audio (under
128 ms) must be saved as WAV instead of AAC.

## Browser interlinear slicer

The separate browser workspace in `web/` works locally without uploading audio or
installing dependencies. Start it from the repository root with `python3 -m http.server 8000`, then open
`http://localhost:8000/web/` in a recent browser. Microphone recording requires a secure
context (localhost or HTTPS). This is separate from the Android project's stored clips
and its word-order presets.

Load a WAV, MP3, M4A, or supported microphone recording spoken in the target language
and an interlinear JSON array in target reading order, for example:

```json
[
  {"target_text": "word", "ancient_text": "λόγος", "spoken_index": 2},
  {"target_text": "was", "ancient_text": "ἦν", "spoken_index": 1},
  {"target_text": "implied word", "ancient_text": ""}
]
```

`spoken_index` is optional and 1-based; it points to a detected waveform slice, not to
a verified word. Duplicate indices are left unassigned. Alternatively, enter a
target-language transcript in spoken order, with one whitespace-separated word per
detected slice, to pair unique matches to the target text. A multiword card can match
consecutive slices. Matching ignores case and surrounding punctuation but preserves
meaningful diacritics. Repeated or overlapping matches remain unassigned. Initial
detection uses quiet gaps and may split syllables or join words. Optionally load
target-language reference clips named after their target text, such as `word.wav`,
and choose **Suggest acoustic matches**. This compares local spectral
features against unassigned slices without assuming their order; same-speaker clips work
best. It is an experimental similarity heuristic, not a phonetic recognizer, and can
produce incorrect matches or leave words unmatched. Reference clips stay local and must
be loaded again after reopening. Check all pairings and boundaries; repeated words are
intentionally not matched automatically. Select an unassigned slice to attach it, or add a slice at the waveform
cursor. Drag the selected handles or enter milliseconds; the handles snap to nearby
zero crossings. Cards can be reordered, split or merged, and audio can be moved separately.
Overwriting an attachment or the whole recording requires reviewing the replacement.
The ancient text is a label for its translated equivalent; it is not compared with
the recording.

Export produces a ZIP with lossless clips in card order, the original source audio and
`alignment.json`. Empty cards have no WAV; merged cards include all their spans in
playback order. To continue editing, load the exported source audio and then its
`alignment.json`; the source hash must match. Edits are otherwise held only in the
current browser session. Global padding is applied at export, and short fades are
applied at clip edges. Neither the source recording nor the timestamp map is modified
by padding or fades. Run the browser core tests with
`node --test web/core.test.mjs web/acoustic.test.mjs`.
With Google Chrome installed, `node --test web/browser.test.mjs` also exercises the
browser workflow.

## John 3:16 editor demo

The Android CI workflow has a manual `clip_demo` option. Select it to build and upload
only the standalone `john-3-16-clip-demo` debug APK; it skips the full Greek assets and
other CI jobs. The demo opens the production verse editor directly with a synthetic
waveform, not speech. Record a verse to replace it; demo clips are isolated from the
normal app. Locally, build the same APK with `./gradlew -PclipDemo :app:assembleDebug`.

## Acknowledgements

Thank you to [MaculaScripta](https://github.com/danzuep/MaculaScripta) for the audio
workbench and plan generator, and to [BTT Recorder](https://github.com/Bible-Translation-Tools/BTT-Recorder)
for inspiration from its recording and take-review workflows. This project also builds on
the upstream [Story Producer](https://github.com/sillsdev/StoryProducer) repository.

## Development

Android 6.0 (API 23) or later is required. Build and test with the Gradle-pinned JDK 17,
the Android SDK, Node.js, curl, and network access to GitHub. Gradle uses JDK 17 for its
build daemon even when the shell's default Java is newer:

```sh
./gradlew :core:jvmTest :app:testDebugUnitTest :app:assembleDebug
```

`core` contains platform-independent models and workbench rules in Kotlin Multiplatform
`commonMain`. The Android app owns file storage, JSON, audio recording, playback, and UI.
The Android application ID is `io.github.danzuep.bibleaudioproducer`; it is a new app,
not an upgrade of Story Producer.

Every build, including CI and releases, generates the bundled assets from pinned source
revisions, including [MACULA Greek](https://github.com/Clear-Bible/macula-greek/tree/8423afe47b9e8f24b7772e808af45c7159a6fe7e).
The plan generator and its revision are specified in `scripts/generate-macula-assets.sh`.
The generator checks the complete 27-book, 4,917-card plan rather than accepting a sample.
Gradle reuses generated assets until its inputs change or the build directory is cleaned.
An existing imported plan remains in place; clearing saved data restores the bundled plan.

The plan and verse data are adapted from the
[MACULA Greek Linguistic Datasets](https://github.com/Clear-Bible/macula-greek/),
© 2022–2024 Biblica, Inc., and the
[SBL Greek New Testament](https://github.com/LogosBible/SBLGNT/),
© 2010 Society of Biblical Literature and Logos Bible Software, both licensed
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Additional third-party
attribution and license text are available in the app's settings.

## Releases

Pull requests merged into `main` with a `major:`, `minor:`, or `patch:` title prefix
create a version tag and dispatch the APK release workflow. The `release` environment
can use `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` for
stable release signing. If all four are absent, Gradle signs with a temporary debug key:
the APK names include `temporary-key`, and tagged releases are marked as prereleases.
These APKs cannot upgrade installations signed with a different key. Configure all four
secrets together for a production release; partial configuration fails the workflow.
Manual runs without a version tag use the `dry-run` environment.
When no version tags exist, the next `major:` PR starts at `v1.0.0`.
