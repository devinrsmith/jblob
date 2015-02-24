package com.devinrsmith.jblob.s3;

import com.amazonaws.AmazonClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.Optional;

/**
 * Created by dsmith on 2/22/15.
 */
public interface S3Helper {
    public static S3Helper of(AWSCredentials credentials, Regions regions) {
        return new Impl(credentials, Region.getRegion(regions));
    }

    AmazonS3Client getClient(Region region);
    TransferManager getTransfer(Region region);
    Optional<Region> getBucketLocation(String bucket) throws IOException;

    static class Impl implements S3Helper {

        private final Region defaultRegion;
        private final LoadingCache<Region, AmazonS3Client> clientMap;
        private final LoadingCache<Region, TransferManager> transferMap;

        private Impl(AWSCredentials credentials, Region defaultRegion) {
            this.clientMap = CacheBuilder.newBuilder().build(new CacheLoader<Region, AmazonS3Client>() {
                @Override
                public AmazonS3Client load(Region region) throws Exception {
                    final AmazonS3Client client = new AmazonS3Client(credentials);
                    client.setRegion(region);
                    return client;
                }
            });
            this.transferMap = CacheBuilder.newBuilder().build(new CacheLoader<Region, TransferManager>() {
                @Override
                public TransferManager load(Region key) throws Exception {
                    return new TransferManager(clientMap.get(key));
                }
            });
            this.defaultRegion = defaultRegion;
        }

        @Override
        public AmazonS3Client getClient(Region region) {
            return clientMap.getUnchecked(region);
        }

        @Override
        public TransferManager getTransfer(Region region) {
            return transferMap.getUnchecked(region);
        }

        @Override
        public Optional<Region> getBucketLocation(String bucket) throws IOException {
            try {
                return Optional.of(Region.getRegion(Regions.fromName(getClient(defaultRegion).getBucketLocation(bucket))));
            } catch (AmazonClientException e) {
                if (e instanceof AmazonS3Exception) {
                    if (((AmazonS3Exception)e).getStatusCode() == HttpStatus.SC_NOT_FOUND) {
                        return Optional.empty();
                    }
                }
                throw new IOException(e);
            }
        }
    }
}
