package com.devinrsmith.jblob.api;

/**
* Created by dsmith on 2/24/15.
*/
public interface JBlobStatistics {
    static JBlobStatistics of(long count, long size, long min, long max) {
        return new JBlobStatisticsImpl(count, size, min, max);
    }

    long count();
    long size();
    long min();
    long max();

    default long average() {
        return size() / count();
    }

    static class JBlobStatisticsImpl implements JBlobStatistics {
        private final long count;
        private final long size;
        private final long min;
        private final long max;

        private JBlobStatisticsImpl(long count, long size, long min, long max) {
            this.count = count;
            this.size = size;
            this.min = min;
            this.max = max;
        }

        @Override
        public long size() {
            return size;
        }

        @Override
        public long count() {
            return count;
        }

        @Override
        public long min() {
            return min;
        }

        @Override
        public long max() {
            return max;
        }
    }
}
