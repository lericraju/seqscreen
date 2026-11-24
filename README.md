# SeqScreen

Motif screening for DNA sequence collections. Java 17 + Spring Boot backend, one plain-JS page
served by the same process.

Given a motif, SeqScreen tells you **which sequences contain it and where**. A k-mer index (or a
set of Bloom filters) rules out the sequences that cannot match, and KMP confirms every surviving
position exactly, so screening never drops a real hit.

## Algorithms

| Technique | Where | Complexity |
|---|---|---|
| Inverted k-mer index | `index/KmerIndex` | build O(total bases), lookup O(1) expected |
| KMP | `algo/KmpMatcher` | O(L + m) per sequence, O(m) space |
| Bloom filter | `algo/BloomFilter` | O(L) build, `L log(1/p)` bits per sequence |
| Myers bit-vector | `algo/MotifPattern` | O(L) per sequence for up to 5 edits |
| Naive baseline | `algo/NaiveMatcher` | O(L·m) worst case |
| Reverse complement | `algo/RevComp` | O(m) |

- **The index** maps every distinct k-mer to the sequences containing it. A query takes three of
  the motif's rarest k-mers and keeps only sequences holding one of them. Probing a handful of
  rare k-mers is what makes this cheap: the rarest k-mer of a 13-base motif typically occurs in a
  few sequences out of thousands.
- **Bloom filters** are the fallback when the index would not fit: one small filter per sequence
  over its own k-mers. A miss is a guaranteed absence; a hit is confirmed by KMP.
- **KMP** is the authoritative matcher in every mode. It reports all overlapping positions and
  handles sequences containing `N` by simply not matching them.
- **Myers' bit-vector** handles up to 5 substitutions, insertions or deletions in a single pass
  over the text, with `N` as a wildcard on either side.
- **Reverse complement** means every search covers both strands by default. Hits are reported once,
  in forward coordinates, and flagged when they come only from the reverse strand.
- **Threads** split the candidate list across up to 8 workers; results merge in dataset order, so
  the answer does not depend on scheduling.

## Prerequisites

JDK 17+ and Maven 3.8+.

## Run

```
mvn spring-boot:run
```

Open http://localhost:8080. For large datasets: `MAVEN_OPTS="-Xmx4g" mvn spring-boot:run`, or
`java -Xmx4g -jar target/seqscreen-1.0.0.jar` after `mvn package`.

## Test

```
mvn test
```

60 tests: KMP (9), Bloom filter (8), inverted index (7), FASTA parsing (9), approximate matching
and reverse complement (9), search service across all modes (18). The approximate-matching suite
checks 500 random cases against a dynamic-programming baseline.

## Workflow

1. The app loads a sample dataset on first use, or load your own from **Options**: generate
   synthetic sequences or upload a FASTA file.
2. Type a motif (`A`, `C`, `G`, `T`, `N` = any base) and press Enter.
3. Read the hits: sequence id, position, strand, and the bases around the match. **Options** adds
   allowed edits, workers, the screening strategy and a single-strand toggle.
4. **Benchmark** compares naive, KMP, Bloom+KMP and index+KMP on growing datasets, including index
   build time, memory and an exactness check per row.

## API

```
# synthetic dataset (all fields optional; these are the defaults)
curl -X POST localhost:8080/api/dataset/generate -H "Content-Type: application/json" \
  -d '{"count":10000,"length":1000,"seed":42,"plantedFraction":0.01,"plantedMotif":"GATTACAGATTACA","k":8,"p":0.01}'

# FASTA upload (A, C, G, T, N)
curl -X POST localhost:8080/api/dataset/upload -F file=@sample-data/sample.fasta -F k=8 -F p=0.01

# current dataset
curl localhost:8080/api/dataset

# exact search, both strands, automatic workers
curl -X POST localhost:8080/api/search -H "Content-Type: application/json" \
  -d '{"motif":"GATTACAGATTACA","mode":"INDEX_KMP"}'

# up to one edit, forward strand only, four workers, Bloom screening
curl -X POST localhost:8080/api/search -H "Content-Type: application/json" \
  -d '{"motif":"GATTACAGATTACA","mode":"BLOOM_KMP","mismatches":1,"bothStrands":false,"threads":4}'

# wildcard motif
curl -X POST localhost:8080/api/search -H "Content-Type: application/json" \
  -d '{"motif":"GATTNCA","mode":"INDEX_KMP"}'

curl -X POST localhost:8080/api/benchmark -H "Content-Type: application/json" \
  -d '{"sizes":[1000,10000,100000],"length":500,"motif":"GATTACAGATTACA","k":8,"p":0.01}'
```

`mode` is `INDEX_KMP` (default), `BLOOM_KMP`, `KMP` or `NAIVE`. A search response carries
`matches` (sequence id, position, strand, edits, context) and `stats` (screened-out count,
candidates, confirmed sequences, false positives, screening note, workers, elapsed ms).
Errors return `{"error": "..."}` with HTTP 400 (bad input), 409 (no dataset), 413 (too large),
507 (out of memory).

## Input rules and failure cases

- Sequence data accepts A, C, G, T and N; any other character rejects the file
  ([sample-data/invalid.fasta](sample-data/invalid.fasta)).
- `N` in a sequence is an unknown base: it never matches a known base in exact search, and matches
  any base in approximate or wildcard search.
- `N` in a motif is a wildcard. A wildcard motif, or any search with edits > 0, scans every
  sequence: an exact k-mer filter cannot bound an edited match. The response says so in
  `stats.screeningNote`.
- Approximate and wildcard search handle motifs up to 64 bases (one machine word); longer motifs
  must be searched exactly (up to 512 bases).
- Motif shorter than k: screening is skipped and KMP runs on every sequence.
- The index is built when `k ≤ 16` and the dataset has at most 16M k-mer occurrences; otherwise
  only Bloom filters are built, and `indexNote` says why.
- Duplicate FASTA ids: the first is kept, the rest are skipped and counted. Empty records are
  skipped and counted.
- Limits: 150M total bases per dataset, 2000 reported matches per query, benchmark sizes up to
  200k sequences and 60M bases per size.

## Layout

```
algo/        KmpMatcher, NaiveMatcher, BloomFilter, KmerEncoder, RevComp, MotifPattern, IntList
index/       KmerIndex, SequenceIndex, SequenceRecord
service/     DatasetService, SearchService, SearchMode, SearchOptions, SearchOutcome, Threads,
             BenchmarkService
controller/  DatasetController, SearchController, BenchmarkController, GlobalExceptionHandler
dto/         request/response records
validation/  FastaParser, MotifValidator, ApiException
static/      index.html — the whole frontend
```

## Sample data

- [sample-data/sample.fasta](sample-data/sample.fasta) — 30 synthetic sequences for upload testing.
- [sample-data/reverse-strand.fasta](sample-data/reverse-strand.fasta) — the motif on the forward
  strand, the same motif present only as its reverse complement, and a control. Search
  `GATTACAGATTACA` to see one hit flagged `forward` and one flagged `reverse`.
- [sample-data/invalid.fasta](sample-data/invalid.fasta) — contains `X`, so it must be rejected.
