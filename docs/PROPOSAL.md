# Project proposal — SeqScreen

**Course:** Advanced Algorithm (CIA 3) · Semester V · B.Tech CS (AI & ML)

**Team:** Leric Raju (2409308) · Mohammed Yusuf (2410309) · Tabish Matwankar (2410672)

## Problem

A lab holds thousands of DNA sequences — isolates, reads, reference entries — and repeatedly needs
to know which of them contain a particular short motif: a primer binding site, a restriction site,
a marker sequence. Scanning every sequence in full for every query is wasteful, because in a
typical screening run the overwhelming majority of sequences do not contain the motif at all.

The cost is not the matching itself; it is that a full scan pays for every base of every sequence
to answer a question that a small number of bases could have settled.

## Target user

A lab technician or bioinformatics student who uploads a FASTA file, types a motif, and needs the
matching sequences with exact positions. They care about three things: no missed hits, positions
they can trust, and an answer fast enough to try several motifs in a row.

## Proposed solution

A web application in which the Java backend does all the algorithm work:

1. **Build once, per dataset.** Encode every k-mer (default `k = 8`) as a 2-bit-per-base integer
   and index it to the sequences that contain it, inverted-index style. A per-sequence Bloom filter
   is built as well, for datasets too large to index.
2. **Screen.** For a motif, look up a few of its rarest k-mers in the index. Only sequences holding
   one of them can possibly contain the motif; everything else is rejected without being read.
3. **Confirm.** Run KMP on each surviving sequence to get exact, overlapping positions. The filter
   only ever narrows the search — it can never remove a real hit.
4. **Report.** Sequence id, position, strand, and the surrounding bases.

## Core techniques

| Technique | Unit | Why it is appropriate |
|---|---|---|
| **KMP** | 4 (text search) | Exact matching in O(L + m) instead of O(L·m), reports every overlapping occurrence, and is the authoritative check behind every screen. |
| **Inverted k-mer index** | 4 (tries / indexes) | Turns "which sequences contain this motif" into a handful of hash lookups over k-mer postings, so the work scales with the number of candidates rather than the dataset. |
| **Bloom filter** | 6 (randomisation, CO5) | A probabilistic membership structure with no false negatives and a tunable false-positive rate; it is the fallback screen when the index does not fit in memory. Every positive is confirmed exactly. |
| **Myers bit-vector** | 5 (approximation) | Approximate matching in O(L) per sequence for up to five edits, needed because real sequence data carries substitutions and sequencing errors. |

Units 4 and 6 are both represented, and CO5 is met by the Bloom filter.

## Data

- A seeded synthetic generator: random A/C/G/T strings with a configurable fraction of sequences
  carrying a planted motif. Seeded so every benchmark is reproducible.
- FASTA upload for real data (`A`, `C`, `G`, `T`, `N`), validated on the way in.
- Sample files in `sample-data/`, including one that must be rejected.

## Measurable success criteria

1. **No false negatives.** For every query, all four strategies must report an identical hit list;
   the benchmark fails a row if they disagree, and the test suite asserts the same property.
2. **False positives within the configured target.** The Bloom filter is sized from `p` and its
   observed rate is reported; a rate materially above target is a bug, not a trade-off.
3. **A real speed-up on rare motifs.** The index must beat a full KMP scan by at least 5× at
   10,000 sequences, measured on the same data with the same motif.
4. **Honest reporting of where it does not help.** Common or repetitive motifs reduce the benefit;
   the benchmark reports those cases in the same table rather than hiding them.

## Scope and limits

In scope: exact and approximate motif search, both strands, synthetic and uploaded datasets,
benchmarking. Out of scope: persistent storage, multiple concurrent users, alignment against
genome-scale references, and any client-side algorithm — the backend owns every algorithm.
