/*
 * Copyright (C) 2016 Brian Wernick,
 * Copyright (C) 2015 Sébastiaan Versteeg,
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.devbrackets.android.exomedia.core.builder;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaCodec;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.devbrackets.android.exomedia.core.exoplayer.EMExoPlayer;
import com.devbrackets.android.exomedia.core.renderer.EMMediaCodecAudioTrackRenderer;
import com.devbrackets.android.exomedia.util.MediaUtil;
import com.google.android.exoplayer.MediaCodecSelector;
import com.google.android.exoplayer.MediaCodecVideoTrackRenderer;
import com.google.android.exoplayer.TrackRenderer;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.drm.MediaDrmCallback;
import com.google.android.exoplayer.extractor.ExtractorSampleSource;
import com.google.android.exoplayer.text.TextTrackRenderer;
import com.google.android.exoplayer.upstream.Allocator;
import com.google.android.exoplayer.upstream.DataSource;
import com.google.android.exoplayer.upstream.DefaultAllocator;
import com.google.android.exoplayer.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer.upstream.DefaultUriDataSource;
import com.google.android.exoplayer.upstream.TransferListener;

/**
 * A default RenderBuilder that can process general
 * media urls including mkv, mp3, mp4, aac, etc.
 */
@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
public class RenderBuilder {
    protected static final int DROPPED_FRAME_NOTIFICATION_AMOUNT = 50;
    protected static final long MAX_JOIN_TIME = 5000;

    protected static final int BUFFER_SEGMENT_SIZE = 64 * 1024;
    protected static final int BUFFER_SEGMENTS_VIDEO = 200;
    protected static final int BUFFER_SEGMENTS_AUDIO = 54;
    protected static final int BUFFER_SEGMENTS_TEXT = 2;
    protected static final int BUFFER_SEGMENTS_TOTAL = BUFFER_SEGMENTS_VIDEO + BUFFER_SEGMENTS_AUDIO + BUFFER_SEGMENTS_TEXT;

    @NonNull
    protected final Context context;
    @NonNull
    protected final String userAgent;
    @NonNull
    protected final String uri;
    @Nullable
    protected final MediaDrmCallback drmCallback;
    protected final int streamType;

    @Nullable
    protected AsyncBuilder asyncBuilder;

    public RenderBuilder(@NonNull Context context, @NonNull String userAgent, @NonNull String uri) {
        this(context, userAgent, uri, AudioManager.STREAM_MUSIC);
    }

    public RenderBuilder(@NonNull Context context, @NonNull String userAgent, @NonNull String uri, int streamType) {
        this(context, userAgent, uri, null, streamType);
    }

    public RenderBuilder(@NonNull Context context, @NonNull String userAgent, @NonNull String uri, @Nullable MediaDrmCallback drmCallback, int streamType) {
        this.uri = uri;
        this.userAgent = userAgent;
        this.context = context;
        this.drmCallback = drmCallback;
        this.streamType = streamType;
    }

    @NonNull
    public Context getContext() {
        return context;
    }

    public void buildRenderers(EMExoPlayer player) {
        asyncBuilder = createAsyncBuilder(player);
        asyncBuilder.init();
    }

    public void cancel() {
        if (asyncBuilder != null) {
            asyncBuilder.cancel();
            asyncBuilder = null;
        }
    }

    protected AsyncBuilder createAsyncBuilder(EMExoPlayer player) {
        return new AsyncBuilder(context, userAgent, uri, drmCallback, player, streamType);
    }

    protected DataSource createDataSource(Context context, TransferListener transferListener, String userAgent) {
        return new DefaultUriDataSource(context, transferListener, userAgent, true);
    }

    protected class AsyncBuilder {
        @NonNull
        protected final Context context;
        @NonNull
        protected final String userAgent;
        @NonNull
        protected final String uri;
        @Nullable
        protected final MediaDrmCallback drmCallback;
        @NonNull
        protected final EMExoPlayer player;
        protected final int streamType;

        protected boolean canceled;

        public AsyncBuilder(@NonNull Context context, @NonNull String userAgent, @NonNull String uri, @Nullable MediaDrmCallback drmCallback, @NonNull EMExoPlayer player, int streamType) {
            this.context = context;
            this.userAgent = userAgent;
            this.uri = uri;
            this.drmCallback = drmCallback;
            this.player = player;

            this.streamType = streamType;
        }

        public void init() {
            //Create the Sample Source to be used by the renderers
            Allocator allocator = new DefaultAllocator(BUFFER_SEGMENT_SIZE);
            DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter(player.getMainHandler(), player);
            DataSource dataSource = createDataSource(context, bandwidthMeter, userAgent);

            ExtractorSampleSource sampleSource = new ExtractorSampleSource(Uri.parse(MediaUtil.getUriWithProtocol(uri)), dataSource,
                    allocator, BUFFER_SEGMENT_SIZE * BUFFER_SEGMENTS_TOTAL, player.getMainHandler(), player, 0);

            //Create the Renderers
            MediaCodecVideoTrackRenderer videoRenderer = new MediaCodecVideoTrackRenderer(context, sampleSource, MediaCodecSelector.DEFAULT,
                    MediaCodec.VIDEO_SCALING_MODE_SCALE_TO_FIT, MAX_JOIN_TIME, player.getMainHandler(), player, DROPPED_FRAME_NOTIFICATION_AMOUNT);
            EMMediaCodecAudioTrackRenderer audioRenderer = new EMMediaCodecAudioTrackRenderer(sampleSource, MediaCodecSelector.DEFAULT, null, true,
                    player.getMainHandler(), player, AudioCapabilities.getCapabilities(context), streamType);
            TrackRenderer captionsRenderer = new TextTrackRenderer(sampleSource, player, player.getMainHandler().getLooper());


            //Create the Render list to send to the callback
            TrackRenderer[] renderers = new TrackRenderer[EMExoPlayer.RENDER_COUNT];
            renderers[EMExoPlayer.RENDER_VIDEO] = videoRenderer;
            renderers[EMExoPlayer.RENDER_AUDIO] = audioRenderer;
            renderers[EMExoPlayer.RENDER_CLOSED_CAPTION] = captionsRenderer;
            player.onRenderers(renderers, bandwidthMeter);
        }

        public void cancel() {
            canceled = true;
        }
    }
}