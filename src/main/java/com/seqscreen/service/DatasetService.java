package com.seqscreen.service;

import com.seqscreen.dto.DatasetInfo;
import com.seqscreen.dto.GenerateRequest;
import com.seqscreen.index.SequenceIndex;
import com.seqscreen.index.SequenceRecord;
import com.seqscreen.validation.ApiException;
import com.seqscreen.validation.FastaParser;
import com.seqscreen.validation.MotifValidator;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the dataset currently loaded in this process — one dataset at a time, which is what a
 * single-user screening tool needs and what keeps the memory footprint predictable.
 */
@Service
public class DatasetService {

    public static final int MAX_BASES = 150_000_000;

    public static final int DEFAULT_COUNT = 10_000;
    public static final int DEFAULT_LENGTH = 1_000;
    public static final long DEFAULT_SEED = 42L;
    public static final double DEFAULT_PLANTED_FRACTION = 0.01;
    public static final String DEFAULT_MOTIF = "GATTACAGATTACA";
    public static final int DEFAULT_K = 8;
    public static final double DEFAULT_P = 0.01;

    private final AtomicReference<SequenceIndex> current = new AtomicReference<>();
    private volatile String source = "none";

    public DatasetInfo generate(GenerateRequest request) {
        GenerateRequest req = request == null ? new GenerateRequest(null, null, null, null, null, null, null)
                : request;
        int count = value(req.count(), DEFAULT_COUNT);
        int length = value(req.length(), DEFAULT_LENGTH);
        long seed = req.seed() == null ? DEFAULT_SEED : req.seed();
        double planted = req.plantedFraction() == null ? DEFAULT_PLANTED_FRACTION : req.plantedFraction();
        String motif = req.plantedMotif() == null || req.plantedMotif().isBlank()
                ? DEFAULT_MOTIF : req.plantedMotif().strip().toUpperCase();
        int k = value(req.k(), DEFAULT_K);
        double p = req.p() == null ? DEFAULT_P : req.p();

        if (count < 1 || count > 1_000_000) {
            throw ApiException.badRequest("Sequence count must be between 1 and 1,000,000.");
        }
        if (length < 20 || length > 100_000) {
            throw ApiException.badRequest("Sequence length must be between 20 and 100,000 bases.");
        }
        if (planted < 0 || planted > 1) {
            throw ApiException.badRequest("Planted fraction must be between 0 and 1.");
        }
        MotifValidator.validateK(k);
        MotifValidator.validateP(p);
        MotifValidator.validate(motif, 0);
        if ((long) count * length > MAX_BASES) {
            throw new ApiException(413, "That dataset is " + (long) count * length
                    + " bases; the limit is " + MAX_BASES + ".");
        }

        List<SequenceRecord> records = synthesize(count, length, seed, planted, motif);
        return install(records, k, p, "synthetic " + count + " x " + length + " bp, seed " + seed);
    }

    public DatasetInfo upload(MultipartFile file, Integer k, Double p) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a FASTA file to upload.");
        }
        int kmerSize = value(k, DEFAULT_K);
        double rate = p == null ? DEFAULT_P : p;
        MotifValidator.validateK(kmerSize);
        MotifValidator.validateP(rate);

        String text;
        try {
            text = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read the file: " + e.getMessage());
        }
        FastaParser.Result parsed = FastaParser.parse(text);
        long bases = 0;
        for (SequenceRecord record : parsed.records()) {
            bases += record.bases().length();
        }
        if (bases > MAX_BASES) {
            throw new ApiException(413, "That file holds " + bases + " bases; the limit is "
                    + MAX_BASES + ".");
        }
        String label = (file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename())
                + (parsed.skippedDuplicates() > 0 ? " · " + parsed.skippedDuplicates() + " duplicate ids skipped" : "")
                + (parsed.skippedEmpty() > 0 ? " · " + parsed.skippedEmpty() + " empty records skipped" : "");
        return install(parsed.records(), kmerSize, rate, label);
    }

    public DatasetInfo info() {
        SequenceIndex index = current.get();
        if (index == null) {
            return new DatasetInfo(0, 0, 0, 0, false, 0, 0, 0, "none",
                    "No dataset loaded.", "No dataset loaded.");
        }
        return describe(index, source);
    }

    public SequenceIndex require() {
        SequenceIndex index = current.get();
        if (index == null) {
            throw ApiException.noDataset();
        }
        return index;
    }

    /** Synthetic DNA with the motif planted at random positions in a fraction of sequences. */
    public static List<SequenceRecord> synthesize(int count, int length, long seed,
                                                  double plantedFraction, String motif) {
        SplittableRandom random = new SplittableRandom(seed);
        char[] bases = {'A', 'C', 'G', 'T'};
        int planted = (int) Math.round(count * plantedFraction);
        List<SequenceRecord> records = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            char[] sequence = new char[length];
            for (int j = 0; j < length; j++) {
                sequence[j] = bases[random.nextInt(4)];
            }
            boolean place = i < planted && length >= motif.length();
            if (place) {
                int at = random.nextInt(length - motif.length() + 1);
                System.arraycopy(motif.toCharArray(), 0, sequence, at, motif.length());
            }
            records.add(new SequenceRecord("seq_" + (i + 1), new String(sequence)));
        }
        return records;
    }

    private DatasetInfo install(List<SequenceRecord> records, int k, double p, String label) {
        SequenceIndex built;
        try {
            built = SequenceIndex.build(records, k, p);
        } catch (OutOfMemoryError error) {
            throw new ApiException(507, "Not enough memory for this dataset. Raise the heap with "
                    + "-Xmx4g, or use fewer sequences.");
        }
        current.set(built);
        source = label;
        return describe(built, label);
    }

    private DatasetInfo describe(SequenceIndex index, String label) {
        return new DatasetInfo(index.size(), index.totalBases(), index.k(), index.falsePositiveRate(),
                index.indexBuilt(), index.indexBytes(), index.bloomBytes(), index.distinctKmers(),
                label, index.indexNote(), index.bloomNote());
    }

    private static int value(Integer supplied, int fallback) {
        return supplied == null ? fallback : supplied;
    }
}
