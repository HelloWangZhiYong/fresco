package com.facebook.fresco.helper.config;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Bitmap;

import com.facebook.cache.disk.DiskCacheConfig;
import com.facebook.common.internal.Preconditions;
import com.facebook.common.internal.Supplier;
import com.facebook.common.memory.MemoryTrimType;
import com.facebook.common.memory.MemoryTrimmable;
import com.facebook.common.memory.MemoryTrimmableRegistry;
import com.facebook.common.memory.NoOpMemoryTrimmableRegistry;
import com.facebook.common.util.ByteConstants;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.fresco.helper.utils.MLog;
import com.facebook.imagepipeline.cache.MemoryCacheParams;
import com.facebook.imagepipeline.core.ImagePipelineConfig;
import com.facebook.imagepipeline.listener.RequestListener;
import com.facebook.imagepipeline.listener.RequestLoggingListener;
import com.facebook.imagepipeline.producers.HttpUrlConnectionNetworkFetcher;
import com.facebook.imagepipeline.producers.NetworkFetcher;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * new PhoenixConfig.Builder(context)
 * .setNetworkFetcher(null)
 * .setRequestListeners(null)
 * .setBitmapMemoryCacheParamsSupplier(null)
 * .setMemoryTrimmableRegistry(null)
 * .setMainDiskCacheConfig(null)
 * .setSmallImageDiskCacheConfig(null)
 * .build();
 * <p>
 * File fileCacheDir = mContext.getApplicationContext().getFilesDir();
 * File fileCacheDir = null;
 * if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
 * fileCacheDir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/Fresco");
 * }
 * MLog.i("getAbsolutePath = " + fileCacheDir.getAbsolutePath());
 * <p>
 * Created by android_ls on 16/9/8.
 */
public class PhoenixConfig {

    private PhoenixConfig() {
        // FLog.setMinimumLoggingLevel(FLog.VERBOSE);
    }

    public static ImagePipelineConfig get(Context context) {
        return new PhoenixConfig.Builder(context).build();
    }

    public static class Builder {

        private static final String IMAGE_PIPELINE_CACHE_DIR = "image_cache";
        private static final String IMAGE_PIPELINE_SMALL_CACHE_DIR = "image_small_cache";
        private static final int MAX_DISK_SMALL_CACHE_SIZE = 10 * ByteConstants.MB;
        private static final int MAX_DISK_SMALL_ONLOWDISKSPACE_CACHE_SIZE = 5 * ByteConstants.MB;

        private final Context mContext;
        private Set<RequestListener> mRequestListeners;
        private MemoryTrimmableRegistry mMemoryTrimmableRegistry;
        private Supplier<MemoryCacheParams> mBitmapMemoryCacheParamsSupplier;
        private DiskCacheConfig mMainDiskCacheConfig;
        private DiskCacheConfig mSmallImageDiskCacheConfig;
        private NetworkFetcher mNetworkFetcher;

        public Builder(Context context) {
            mContext = Preconditions.checkNotNull(context);
        }

        public Builder setNetworkFetcher(NetworkFetcher networkFetcher) {
            mNetworkFetcher = networkFetcher;
            return this;
        }

        public Builder setRequestListeners(Set<RequestListener> requestListeners) {
            mRequestListeners = requestListeners;
            return this;
        }

        public Builder setMemoryTrimmableRegistry(MemoryTrimmableRegistry memoryTrimmableRegistry) {
            mMemoryTrimmableRegistry = memoryTrimmableRegistry;
            return this;
        }

        public Builder setBitmapMemoryCacheParamsSupplier(
                Supplier<MemoryCacheParams> bitmapMemoryCacheParamsSupplier) {
            mBitmapMemoryCacheParamsSupplier = bitmapMemoryCacheParamsSupplier;
            return this;
        }

        public Builder setMainDiskCacheConfig(DiskCacheConfig mainDiskCacheConfig) {
            mMainDiskCacheConfig = mainDiskCacheConfig;
            return this;
        }

        public Builder setSmallImageDiskCacheConfig(DiskCacheConfig smallImageDiskCacheConfig) {
            mSmallImageDiskCacheConfig = smallImageDiskCacheConfig;
            return this;
        }

        public ImagePipelineConfig build() {
            if (mNetworkFetcher == null) {
                mNetworkFetcher = new HttpUrlConnectionNetworkFetcher();
            }

            if (mRequestListeners == null) {
                mRequestListeners = new HashSet<>();
                mRequestListeners.add(new RequestLoggingListener());
            }

            if (mBitmapMemoryCacheParamsSupplier == null) {
                mBitmapMemoryCacheParamsSupplier = new BitmapMemoryCacheParamsSupplier(
                        (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE));
            }

            if (mMemoryTrimmableRegistry == null) {
                // ?????????????????????????????????
                mMemoryTrimmableRegistry = NoOpMemoryTrimmableRegistry.getInstance();
                mMemoryTrimmableRegistry.registerMemoryTrimmable(new MemoryTrimmable() {
                    @Override
                    public void trim(MemoryTrimType trimType) {
                        final double suggestedTrimRatio = trimType.getSuggestedTrimRatio();
                        MLog.i("Fresco onCreate suggestedTrimRatio = " + suggestedTrimRatio);

                        if (MemoryTrimType.OnCloseToDalvikHeapLimit.getSuggestedTrimRatio() == suggestedTrimRatio
                                || MemoryTrimType.OnSystemLowMemoryWhileAppInBackground.getSuggestedTrimRatio() == suggestedTrimRatio
                                || MemoryTrimType.OnSystemLowMemoryWhileAppInForeground.getSuggestedTrimRatio() == suggestedTrimRatio
                                ) {
                            // ??????????????????
                            Fresco.getImagePipeline().clearMemoryCaches();
                        }
                    }
                });
            }

            /*
             * ?????????????????????????????????????????????????????????????????????:
             * 1???????????????????????????????????????????????????
             * 2?????????????????????????????????????????????????????????????????????
             */
            if (mMainDiskCacheConfig == null) {
                File fileCacheDir = mContext.getCacheDir();
                mMainDiskCacheConfig = DiskCacheConfig.newBuilder(mContext)
                        .setBaseDirectoryName(IMAGE_PIPELINE_CACHE_DIR)
                        .setBaseDirectoryPath(fileCacheDir)
                        .build();
            }

            if (mSmallImageDiskCacheConfig == null) {
                File fileCacheDir = mContext.getCacheDir();
                mSmallImageDiskCacheConfig = DiskCacheConfig.newBuilder(mContext)
                        .setBaseDirectoryPath(fileCacheDir)
                        .setBaseDirectoryName(IMAGE_PIPELINE_SMALL_CACHE_DIR)
                        .setMaxCacheSize(MAX_DISK_SMALL_CACHE_SIZE)
                        .setMaxCacheSizeOnLowDiskSpace(MAX_DISK_SMALL_ONLOWDISKSPACE_CACHE_SIZE)
                        .build();
            }

            return ImagePipelineConfig.newBuilder(mContext)
                    .setBitmapsConfig(Bitmap.Config.ARGB_8888) // ???????????????????????????????????????????????????RGB_565???????????????ARGB_8888)
                    .setDownsampleEnabled(true) // ??????????????????????????????????????????PNG???JPG??????WEBP?????????????????????ResizeOptions????????????
//                    .setProgressiveJpegConfig(new ProgressiveJpegConfig() { // ??????Jpeg????????????????????????????????????
//                        @Override
//                        public int getNextScanNumberToDecode(int scanNumber) {
//                            return scanNumber + 2;
//                        }
//
//                        public QualityInfo getQualityInfo(int scanNumber) {
//                            boolean isGoodEnough = (scanNumber >= 5);
//                            return ImmutableQualityInfo.of(scanNumber, isGoodEnough, false);
//                        }
//                    })
                    .setNetworkFetcher(mNetworkFetcher)
                    .setRequestListeners(mRequestListeners)
                    .setMemoryTrimmableRegistry(mMemoryTrimmableRegistry) // ???????????????????????????
                    .setBitmapMemoryCacheParamsSupplier(mBitmapMemoryCacheParamsSupplier) // ??????????????????
                    .setMainDiskCacheConfig(mMainDiskCacheConfig) // ?????????????????????
                    .setSmallImageDiskCacheConfig(mSmallImageDiskCacheConfig) // ???????????????????????????
                    .build();
        }
    }

}
