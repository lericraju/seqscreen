# Team and contributions

**SeqScreen: CIA 3, Advanced Algorithm, Semester V**

| Member | ID |
|---|---|
| Leric Raju | 2409308 |
| Mohammed Yusuf | 2410309 |
| Tabish Matwankar | 2410672 |

Commits in this repository are authored by Leric Raju's account on behalf of the team; the split
below is who owns, presents and answers for each part in the viva.

## Module ownership

| Area | Files | Owner |
|---|---|---|
| Exact matching: KMP, naive baseline | `algo/KmpMatcher`, `algo/NaiveMatcher` | Leric Raju |
| Build, repository, benchmark methodology | `pom.xml`, `service/BenchmarkService`, `docs/` | Leric Raju |
| Probabilistic screening: Bloom filter, k-mer encoding | `algo/BloomFilter`, `algo/KmerEncoder` | Mohammed Yusuf |
| Input validation: FASTA, motifs, error handling | `validation/*` | Mohammed Yusuf |
| False-positive measurement and sizing | `service/SearchService` (screening stats), benchmark runs | Mohammed Yusuf |
| Inverted k-mer index | `index/KmerIndex`, `index/SequenceIndex` | Tabish Matwankar |
| Approximate matching and strands | `algo/MotifPattern`, `algo/RevComp` | Tabish Matwankar |
| Frontend and API surface | `static/index.html`, `controller/*` | Tabish Matwankar |

## What each member should be able to explain

**Leric Raju**: KMP's prefix table and why the text pointer never moves backwards; the benchmark
methodology (same seed, single thread, index build reported separately, exactness check per row).

**Mohammed Yusuf**: why a Bloom filter has no false negatives; how `m` and `k` are derived from `n`
and `p`; why a pass is only "possibly present" and must be confirmed; why the measured rate is near
zero for long motifs and high for a motif of exactly `k` bases.

**Tabish Matwankar**: why the index probes the rarest k-mers and unions their postings (looser than
intersecting them, and compulsory across strands); why Myers' shift must not be seeded; why hitting
index caps falls back to Bloom rather than failing.

Every member should also be able to state the project's one-sentence purpose, the
confirmed ≤ passes ≤ total invariant, and the main limitations.
