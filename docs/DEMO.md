# Demo evidence — 8 minutes

Everything below was run on this repository. Numbers are from the development laptop; re-run once
before the review and quote your own.

## Before you start (do this 5 minutes early)

```
mvn test                       # expect: Tests run: 62, Failures: 0
MAVEN_OPTS="-Xmx2g" mvn spring-boot:run
```

Open <http://localhost:8080>. The page loads a synthetic 5,000 × 1,000 bp dataset on its own and
runs the default query, so nothing looks empty when the examiner arrives.

Keep a second terminal open in the project root for the failure cases.

---

## 1. A normal search — 1 min

**Do:** the page already shows motif `GATTACAGATTACA`. Press Enter.

**Say:** "This asks which of the five thousand sequences contain the motif and where. The line above
the results reads: *5,000 sequences · 50 matches · 1.2 ms · 8 workers*, and the panel under it says
4,607 sequences were rejected without being read."

**Point at:** the highlighted bases. "The yellow band is the motif; everything else is context the
lab needs to judge the hit."

**Expected:** 50 matches, each with an id, a position, and the bases around the hit.

---

## 2. Show that the filter decides what gets read — 2 min

This is the part the rubric asks about, so do not skip it.

**Do:** press the **Bloom filter** button. A panel appears under the readout.

**Say:** "A Bloom filter pass means *possibly present*, never *contains*. Each sequence owns a
filter over its k-mers; the sequence is only read if every k-mer of the motif might be present. So
KMP confirms every pass, and the passes that confirm nothing are counted as false positives."

**Read out:** *Bloom passes 50 · Confirmed by KMP 50 · False positives 0 · Measured rate 0.0%*.

**Now make false positives visible.** Press the preset **GATTACAG** (exactly `k` bases, so a single
k-mer collision is enough to pass), then keep the Bloom mode.

**Expected on the page's default dataset:** *Bloom passes 307 · Confirmed by KMP 194 · False
positives 113 · Measured rate 36.8%*, and the same 208 positions KMP-only reports.

**Say:** "307 sequences passed, 194 really contain the motif, 113 were wasted confirmations. The
answer is still exact — KMP finds the same 208 positions it finds scanning all 5,000 — we just read
6% of the data. A high false-positive rate costs time, never correctness."

**If asked why the rate is so much higher than the 1% target:** the filter is asked whether the
motif's single 8-mer might be present, so each sequence's false-positive rate of about 1% is
offered 5,000 times. With the 14-base motif the filter must pass all 7 k-mers at once, which is why
the rate there is 0%.

**Do:** press **k-mer index** and search `GATTACAGATTACA` again.

**Say:** "The index keeps a sequence if it holds one of the motif's three rarest k-mers — one is
enough evidence, so we union the postings rather than intersect them. Here 393 candidates, of which
50 were real."

| Mode | Read | Rejected | Confirmed |
|---|---:|---:|---:|
| KMP only | 5,000 | 0 | 50 |
| Bloom + KMP | 50 | 4,950 | 50 |
| Index + KMP | 393 | 4,607 | 50 |

---

## 3. Reverse strand — 45 s

**Do:** in **Options**, click **Upload FASTA…** and choose `sample-data/reverse-strand.fasta`,
then search `GATTACAGATTACA`.

**Expected:** two hits, both at position 6 — one labelled `forward strand`, one labelled
`reverse strand`, and the third sequence (`no_hit`) screened out.

**Say:** "DNA is double stranded. The reverse-complement sequence contains no GATTACAGATTACA if you
read it left to right, yet the site is real; we find it because the motif's reverse complement is
searched too, and report it once in forward coordinates."

**Do:** switch **Strands** to *Forward only* and search again — the reverse hit disappears. Switch
back to *Both*.

---

## 4. Approximate matching and N bases — 1 min

**Do:** set **Allowed edits** to `1`, search `GATTACAGATTACA`.

**Expected:** ~157 matches where exact search found 50, and a note saying the search was **not
screened**.

**Say:** "An edit anywhere in the motif changes every k-mer it spans, so no exact filter can rule a
sequence out. Rather than pretend, the app scans everything and says so."

**Do:** press the **GATTNCA** preset.

**Say:** "N is a wildcard. Same consequence — it cannot be screened."

---

## 5. Threads — 30 s

**Do:** mode **KMP only**, dataset 5,000, motif `GATTACAGATTACA`. Change **Workers** from 8 to 1
and search, then back to 8.

**Expected:** same 50 matches; the time drops roughly in proportion (on the 10 Mbp dataset measured
for the report: 103.3 ms at 1 worker, 25.9 ms at 8).

**Say:** "Candidates are split into contiguous chunks and merged in dataset order, so the answer
never depends on how many workers ran."

---

## 6. Failure handling — 1 min

Type each of these and point at the message:

| Input | Expected message |
|---|---|
| `GATXACA` | `'X' is not a DNA base. Use A, C, G, T or N.` |
| empty motif | `Enter a motif to search for.` |
| 70-base motif with 1 edit | `Approximate and wildcard search support motifs up to 64 bases` |
| Upload `sample-data/invalid.fasta` | `Line 2 contains 'X', which is not a DNA base.` |

**Say:** "Bad input is rejected with a specific reason, not silently dropped. A file that cannot be
trusted never becomes a dataset."

**Two more worth knowing if asked:**
- A motif shorter than `k` → screening is skipped and every sequence is searched (the note says so).
- With no dataset loaded, search returns 409 `No dataset is loaded…`.

---

## 7. Benchmark — 2 min

**Do:** click **Run benchmark** and let it finish (30–60 s).

**Expected table** (your machine will differ):

| Sequences | Naive | KMP | Bloom + KMP | Index + KMP | vs KMP | Index build |
|---:|---:|---:|---:|---:|---:|---:|
| 1,000 | 2.7 ms | 2.3 ms | 0.5 ms | 0.1 ms | 18.2× | 34 ms |
| 10,000 | 21.9 ms | 19.4 ms | 2.3 ms | 0.4 ms | 45.1× | 337 ms |
| 100,000 | 249.5 ms | 186.8 ms | 11.4 ms | 9.7 ms * | — | — |

**Say three things:**
1. "Every row is recomputed by all four strategies and checked for identical hits; a row that
   disagreed would be marked invalid."
2. "Index build time is its own column — the speed-up is only real if you account for it."
3. "At 100,000 sequences the index exceeds its 16 M k-mer cap, so it reports itself skipped and the
   query falls back to Bloom screening. That is labelled with an asterisk, not hidden."

---

## Numbers to have at your fingertips

| Question | Answer |
|---|---|
| Tests | 62, 0 failures |
| Rare-motif screening | 50 of 5,000 sequences read, 4,950 rejected |
| Bloom false positives (8-base motif) | 113 of 307 passes, 36.8%, same 208 hits as KMP |
| Index speed-up | 45.1× vs KMP at 10,000 sequences |
| Threads | 103.3 ms → 25.9 ms on 8 workers |
| Memory at 5 Mbp | index 41.6 MB, Bloom 6.0 MB |
| Index caps | `k ≤ 16`, 16 M occurrences, 150 M bases per dataset |

---

## If something breaks on the day

- **Port 8080 in use:** `netstat -ano | findstr :8080`, stop that PID, or run
  `mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081`.
- **Out of memory on the 100k row:** restart with `MAVEN_OPTS="-Xmx4g"`.
- **Page shows no dataset:** press **Rebuild synthetic dataset**.
- **A screenshot is accepted as evidence** if the app refuses to start: `screenshots/` has the
  search, Bloom screening panel, benchmark, reverse-strand and dark-mode views.
