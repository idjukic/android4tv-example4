/*
 * Copyright (C) 2014 iWedia S.A. Licensed under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package com.iwedia.exampleip.dtv;

import android.os.RemoteException;
import android.util.Log;

import com.iwedia.dtv.dtvmanager.DTVManager;
import com.iwedia.dtv.dtvmanager.IDTVManager;
import com.iwedia.dtv.epg.EpgEventType;
import com.iwedia.dtv.route.broadcast.IBroadcastRouteControl;
import com.iwedia.dtv.route.broadcast.RouteDemuxDescriptor;
import com.iwedia.dtv.route.broadcast.RouteFrontendDescriptor;
import com.iwedia.dtv.route.broadcast.RouteFrontendType;
import com.iwedia.dtv.route.broadcast.RouteMassStorageDescriptor;
import com.iwedia.dtv.route.common.ICommonRouteControl;
import com.iwedia.dtv.route.common.RouteDecoderDescriptor;
import com.iwedia.dtv.route.common.RouteInputOutputDescriptor;
import com.iwedia.dtv.service.IServiceControl;
import com.iwedia.dtv.service.ServiceDescriptor;
import com.iwedia.dtv.service.SourceType;
import com.iwedia.dtv.types.InternalException;
import com.iwedia.dtv.types.TimeDate;
import com.iwedia.exampleip.DTVActivity;
import com.iwedia.exampleip.callbacks.EPGCallBack;

import java.util.ArrayList;
import java.util.EnumSet;

/**
 * DVBManager - Class For Handling MW Components.
 */
public class DVBManager {
    public static final String TAG = "DVBManager";
    /** DTV Service Intent Action. */
    private IDTVManager mDTVManager = null;
    private int mCurrentLiveRoute = -1;
    private int mLiveRouteSat = -1;
    private int mLiveRouteTer = -1;
    private int mLiveRouteCab = -1;
    private int mLiveRouteIp = -1;
    private int mPlaybackRouteIDMain = -1;
    private int mRecordRouteTer = -1;
    private int mRecordRouteCab = -1;
    private int mRecordRouteSat = -1;
    private int mRecordRouteIp = -1;
    private int mCurrentRecordRoute = -1;
    /** EPG Filter ID */
    private int mEPGFilterID = -1;
    /** Currently active list in comedia. */
    private static final int CURRENT_LIST_INDEX = 0;
    /** IP stuff */
    private int mCurrentChannelNumberIp = -1;
    private boolean ipAndSomeOtherTunerType = false;
    /** Teletext and subtitle */
    private TeletextSubtitleAudioManager mTeletextSubtitleAudioManager;
    /** Parental control manager. */
    private ParentalManager mParentalManager = null;
    private static DVBManager instance;
    /** EPG CallBack. */
    private EPGCallBack mEpgCallBack = null;

    /**
     * CallBack for currently DVB status.
     */
    public interface DVBStatus {
        /** Update Now Next values. */
        public void updateNowNext();

        /** Event is locked by parental age rating. */
        public void ageLocked(boolean locked);

        /** channel is locked. */
        public void channelLocked(boolean locked);
    }

    /** CallBack for UI. */
    private DVBStatus mDVBStatus = null;

    public static DVBManager getInstance() {
        if (instance == null) {
            instance = new DVBManager();
        }
        return instance;
    }

    private DVBManager() {
        mDTVManager = new DTVManager();
        try {
            initializeDTVService();
        } catch (InternalException e) {
            e.printStackTrace();
        }
    }

    /**
     * Initialize Service.
     * 
     * @throws InternalException
     */
    private void initializeDTVService() throws InternalException {
        initializeRouteId();
        mParentalManager = ParentalManager.getInstance(mDTVManager);
        mTeletextSubtitleAudioManager = TeletextSubtitleAudioManager
                .getInstance(mDTVManager.getTeletextControl(),
                        mDTVManager.getSubtitleControl(),
                        mDTVManager.getAudioControl(),
                        mDTVManager.getDisplayControl());
        mEPGFilterID = mDTVManager.getEpgControl().createEventList();
    }

    /**
     * Registers EPG callback
     */
    public void registerEpgCallback() {
        mEpgCallBack = EPGCallBack.getInstance();
        mDTVManager.getEpgControl()
                .registerCallback(mEpgCallBack, mEPGFilterID);
    }

    /**
     * Initialize Descriptors For Live Route.
     * 
     * @throws RemoteException
     */
    private void initializeRouteId() {
        IBroadcastRouteControl broadcastRouteControl = mDTVManager
                .getBroadcastRouteControl();
        ICommonRouteControl commonRouteControl = mDTVManager
                .getCommonRouteControl();
        /**
         * RETRIEVE DEMUX DESCRIPTOR.
         */
        RouteDemuxDescriptor demuxDescriptor = broadcastRouteControl
                .getDemuxDescriptor(0);
        /**
         * RETRIEVE DECODER DESCRIPTOR.
         */
        RouteDecoderDescriptor decoderDescriptor = commonRouteControl
                .getDecoderDescriptor(0);
        /**
         * RETRIEVING OUTPUT DESCRIPTOR.
         */
        RouteInputOutputDescriptor outputDescriptor = commonRouteControl
                .getInputOutputDescriptor(0);
        /**
         * RETRIEVING MASS STORAGE DESCRIPTOR.
         */
        RouteMassStorageDescriptor massStorageDescriptor = new RouteMassStorageDescriptor();
        massStorageDescriptor = broadcastRouteControl
                .getMassStorageDescriptor(0);
        /**
         * GET NUMBER OF FRONTENDS.
         */
        int numberOfFrontends = broadcastRouteControl.getFrontendNumber();
        /**
         * FIND DVB and IP front-end descriptors.
         */
        EnumSet<RouteFrontendType> frontendTypes = null;
        for (int i = 0; i < numberOfFrontends; i++) {
            RouteFrontendDescriptor frontendDescriptor = broadcastRouteControl
                    .getFrontendDescriptor(i);
            frontendTypes = frontendDescriptor.getFrontendType();
            for (RouteFrontendType frontendType : frontendTypes) {
                switch (frontendType) {
                    case SAT: {
                        if (mLiveRouteSat == -1) {
                            mLiveRouteSat = getLiveRouteId(frontendDescriptor,
                                    demuxDescriptor, decoderDescriptor,
                                    outputDescriptor, broadcastRouteControl);
                        }
                        /**
                         * RETRIEVE RECORD ROUTES
                         */
                        if (mRecordRouteSat == -1) {
                            mRecordRouteSat = broadcastRouteControl
                                    .getRecordRoute(frontendDescriptor
                                            .getFrontendId(), demuxDescriptor
                                            .getDemuxId(),
                                            massStorageDescriptor
                                                    .getMassStorageId());
                        }
                        break;
                    }
                    case CAB: {
                        if (mLiveRouteCab == -1) {
                            mLiveRouteCab = getLiveRouteId(frontendDescriptor,
                                    demuxDescriptor, decoderDescriptor,
                                    outputDescriptor, broadcastRouteControl);
                        }
                        /**
                         * RETRIEVE RECORD ROUTES
                         */
                        if (mRecordRouteCab == -1) {
                            mRecordRouteCab = broadcastRouteControl
                                    .getRecordRoute(frontendDescriptor
                                            .getFrontendId(), demuxDescriptor
                                            .getDemuxId(),
                                            massStorageDescriptor
                                                    .getMassStorageId());
                        }
                        break;
                    }
                    case TER: {
                        if (mLiveRouteTer == -1) {
                            mLiveRouteTer = getLiveRouteId(frontendDescriptor,
                                    demuxDescriptor, decoderDescriptor,
                                    outputDescriptor, broadcastRouteControl);
                        }
                        /**
                         * RETRIEVE RECORD ROUTES
                         */
                        if (mRecordRouteTer == -1) {
                            mRecordRouteTer = broadcastRouteControl
                                    .getRecordRoute(frontendDescriptor
                                            .getFrontendId(), demuxDescriptor
                                            .getDemuxId(),
                                            massStorageDescriptor
                                                    .getMassStorageId());
                        }
                        break;
                    }
                    case IP: {
                        if (mLiveRouteIp == -1) {
                            mLiveRouteIp = getLiveRouteId(frontendDescriptor,
                                    demuxDescriptor, decoderDescriptor,
                                    outputDescriptor, broadcastRouteControl);
                        }
                        /**
                         * RETRIEVE RECORD ROUTES
                         */
                        if (mRecordRouteIp == -1) {
                            mRecordRouteIp = broadcastRouteControl
                                    .getRecordRoute(frontendDescriptor
                                            .getFrontendId(), demuxDescriptor
                                            .getDemuxId(),
                                            massStorageDescriptor
                                                    .getMassStorageId());
                        }
                        break;
                    }
                    default:
                        break;
                }
            }
        }
        /**
         * RETRIEVE PLAYBACK ROUTE
         */
        mPlaybackRouteIDMain = broadcastRouteControl.getPlaybackRoute(
                massStorageDescriptor.getMassStorageId(),
                demuxDescriptor.getDemuxId(), decoderDescriptor.getDecoderId());
        if (mLiveRouteIp != -1
                && (mLiveRouteCab != -1 || mLiveRouteSat != -1 || mLiveRouteTer != -1)) {
            ipAndSomeOtherTunerType = true;
        }
        Log.d(TAG, "mLiveRouteTer=" + mLiveRouteTer + ", mLiveRouteCab="
                + mLiveRouteCab + ", mLiveRouteIp=" + mLiveRouteIp);
        Log.d(TAG, "mRecordRouteTer=" + mRecordRouteTer + ", mRecordRouteCab="
                + mRecordRouteCab + ", mRecordRouteIp=" + mRecordRouteIp);
    }

    /**
     * Get Live Route From Descriptors.
     * 
     * @param fDescriptor
     * @param mDemuxDescriptor
     * @param mDecoderDescriptor
     * @param mOutputDescriptor
     */
    private int getLiveRouteId(RouteFrontendDescriptor fDescriptor,
            RouteDemuxDescriptor mDemuxDescriptor,
            RouteDecoderDescriptor mDecoderDescriptor,
            RouteInputOutputDescriptor mOutputDescriptor,
            IBroadcastRouteControl routeControl) {
        return routeControl.getLiveRoute(fDescriptor.getFrontendId(),
                mDemuxDescriptor.getDemuxId(),
                mDecoderDescriptor.getDecoderId());
    }

    /**
     * Stop MW video playback.
     * 
     * @throws InternalException
     */
    public void stopDTV() throws InternalException {
        if (mTeletextSubtitleAudioManager.isTeletextActive()) {
            mTeletextSubtitleAudioManager.hideTeletext();
        }
        if (mTeletextSubtitleAudioManager.isSubtitleActive()) {
            mTeletextSubtitleAudioManager.hideSubtitles();
        }
        mParentalManager.unregisterCallback();
        ParentalManager.destroyInstance();
        mDTVManager.getVideoControl().videoBlank(mPlaybackRouteIDMain, false);
        mDTVManager.getServiceControl().stopService(mCurrentLiveRoute);
        mDTVManager.getEpgControl().releaseEventList(mEPGFilterID);
        mDTVManager.getEpgControl().unregisterCallback(mEpgCallBack,
                mEPGFilterID);
    }

    /**
     * Change Channel Up.
     * 
     * @return Channel Info Object.
     * @throws InternalException
     * @throws IllegalArgumentException
     */
    public ChannelInfo changeChannelUp() throws InternalException {
        int currentChannel = 0;
        try {
            currentChannel = getCurrentChannelNumber();
        } catch (InternalException e) {
            currentChannel = DTVActivity.getLastWatchedChannelIndex();
        }
        int listSize = getChannelListSize();
        if (listSize == 0) {
            return null;
        }
        return changeChannelByNumber((currentChannel + 1) % listSize);
    }

    /**
     * Change Channel Down.
     * 
     * @return Channel Info Object
     * @throws InternalException
     * @throws IllegalArgumentException
     */
    public ChannelInfo changeChannelDown() throws InternalException {
        int currentChannelNumber = 0;
        try {
            currentChannelNumber = getCurrentChannelNumber();
        } catch (InternalException e) {
            currentChannelNumber = DTVActivity.getLastWatchedChannelIndex();
        }
        int listSize = getChannelListSize();
        if (listSize == 0) {
            return null;
        }
        return changeChannelByNumber((--currentChannelNumber + listSize)
                % listSize);
    }

    /**
     * Change Channel by Number.
     * 
     * @return Channel Info Object or null if error occurred.
     * @throws IllegalArgumentException
     * @throws InternalException
     */
    public ChannelInfo changeChannelByNumber(int channelNumber)
            throws InternalException {
        int listSize = getChannelListSize();
        if (listSize == 0) {
            return null;
        }
        channelNumber = (channelNumber + listSize) % listSize;
        int numberOfDtvChannels = listSize
                - (mLiveRouteIp == -1 ? 0 : DTVActivity.sIpChannels.size());
        /** For regular DVB channel */
        if (channelNumber < numberOfDtvChannels) {
            ServiceDescriptor desiredService = mDTVManager.getServiceControl()
                    .getServiceDescriptor(
                            CURRENT_LIST_INDEX,
                            ipAndSomeOtherTunerType ? channelNumber + 1
                                    : channelNumber);
            int route = getActiveRouteByServiceType(desiredService
                    .getSourceType());
            if (route == -1) {
                return null;
            }
            mCurrentLiveRoute = route;
            mCurrentRecordRoute = getActiveRecordRouteByServiceType(desiredService
                    .getSourceType());
            mDTVManager.getServiceControl()
                    .startService(
                            route,
                            CURRENT_LIST_INDEX,
                            ipAndSomeOtherTunerType ? channelNumber + 1
                                    : channelNumber);
        }
        /** For IP */
        else {
            mCurrentLiveRoute = mLiveRouteIp;
            mCurrentRecordRoute = mRecordRouteIp;
            mCurrentChannelNumberIp = channelNumber;
            mDTVManager.getServiceControl().zapURL(
                    mLiveRouteIp,
                    DTVActivity.sIpChannels.get(
                            channelNumber - numberOfDtvChannels).getUrl());
        }
        if (mDVBStatus != null) {
            mDVBStatus.ageLocked(false);
        }
        if (mDVBStatus != null) {
            mDVBStatus.ageLocked(false);
        }
        DTVActivity.setLastWatchedChannelIndex(channelNumber);
        return getChannelInfo(channelNumber, true);
    }

    /**
     * Return route by service type.
     * 
     * @param serviceType
     *        Service type to check.
     * @return Desired route, or 0 if service type is undefined.
     */
    private int getActiveRouteByServiceType(SourceType sourceType) {
        switch (sourceType) {
            case CAB: {
                return mLiveRouteCab;
            }
            case TER: {
                return mLiveRouteTer;
            }
            case SAT: {
                return mLiveRouteSat;
            }
            case IP: {
                return mLiveRouteIp;
            }
            default:
                return -1;
        }
    }

    private int getActiveRecordRouteByServiceType(SourceType sourceType) {
        switch (sourceType) {
            case CAB: {
                return mRecordRouteCab;
            }
            case TER: {
                return mRecordRouteTer;
            }
            case SAT: {
                return mRecordRouteSat;
            }
            case IP: {
                return mRecordRouteIp;
            }
            default:
                return -1;
        }
    }

    /**
     * Get Size of Channel List.
     */
    public int getChannelListSize() {
        int serviceCount = mDTVManager.getServiceControl().getServiceListCount(
                CURRENT_LIST_INDEX);
        if (ipAndSomeOtherTunerType) {
            serviceCount += DTVActivity.sIpChannels.size();
            serviceCount--;
        } else
        /** Only IP */
        if (mLiveRouteIp != -1) {
            serviceCount = DTVActivity.sIpChannels.size();
        }
        return serviceCount;
    }

    /**
     * Get Channel Names.
     */
    public ArrayList<String> getChannelNames() {
        ArrayList<String> channelNames = new ArrayList<String>();
        String channelName = "";
        int channelListSize = getChannelListSize()
                - (mLiveRouteIp == -1 ? 0 : DTVActivity.sIpChannels.size());
        IServiceControl serviceControl = mDTVManager.getServiceControl();
        /** If there is IP first element in service list is DUMMY */
        channelListSize = ipAndSomeOtherTunerType ? channelListSize + 1
                : channelListSize;
        for (int i = ipAndSomeOtherTunerType ? 1 : 0; i < channelListSize; i++) {
            channelName = serviceControl.getServiceDescriptor(
                    CURRENT_LIST_INDEX, i).getName();
            channelNames.add(channelName);
        }
        /** Add IP */
        if (mLiveRouteIp != -1) {
            for (int i = 0; i < DTVActivity.sIpChannels.size(); i++) {
                channelNames.add(DTVActivity.sIpChannels.get(i).getName());
            }
        }
        return channelNames;
    }

    /**
     * Get Current Time and Date.
     */
    public TimeDate getCurrentTimeDate() {
        return mDTVManager.getSetupControl().getTimeDate();
    }

    /**
     * Get Current Channel Number.
     */
    public int getCurrentChannelNumber() throws InternalException {
        /** For IP */
        if (mCurrentLiveRoute == mLiveRouteIp) {
            return mCurrentChannelNumberIp;
        }
        int current = (mDTVManager.getServiceControl().getActiveService(
                mCurrentLiveRoute).getServiceIndex());
        current = current - (ipAndSomeOtherTunerType ? 1 : 0);
        /** This is error in comedia and should be ignored. */
        if (current < 0) {
            throw new InternalException();
        }
        return current;
    }

    /**
     * Get Current Channel Number and Channel Name.
     * 
     * @return Object of Channel Info class.
     * @throws IllegalArgumentException
     */
    public ChannelInfo getChannelInfo(int channelNumber, boolean channelChange) {
        if (channelNumber < 0 || channelNumber >= getChannelListSize()) {
            return null;
        }
        int numberOfDtvChannels = getChannelListSize()
                - (mLiveRouteIp == -1 ? 0 : DTVActivity.sIpChannels.size());
        /** Return DTV channel. */
        if (channelNumber < numberOfDtvChannels) {
            String channelName = mDTVManager
                    .getServiceControl()
                    .getServiceDescriptor(
                            CURRENT_LIST_INDEX,
                            ipAndSomeOtherTunerType ? channelNumber + 1
                                    : channelNumber).getName();
            if (channelChange) {
                return new ChannelInfo(channelNumber + 1, channelName, null,
                        null);
            } else {
                return new ChannelInfo(channelNumber + 1, channelName,
                        mDTVManager.getEpgControl().getPresentFollowingEvent(
                                mEPGFilterID, channelNumber,
                                EpgEventType.PRESENT_EVENT), mDTVManager
                                .getEpgControl().getPresentFollowingEvent(
                                        mEPGFilterID, channelNumber,
                                        EpgEventType.FOLLOWING_EVENT));
            }
        }
        /** Return IP channel. */
        else {
            return new ChannelInfo(channelNumber + 1, DTVActivity.sIpChannels
                    .get(channelNumber - numberOfDtvChannels).getName(), null,
                    null);
        }
    }

    /**
     * Update Now Next.
     */
    public void updateNowNext() {
        if (mDVBStatus != null) {
            mDVBStatus.updateNowNext();
        }
    }

    /**
     * Update age locked layout.
     */
    public void updateAgeLocked(boolean locked) {
        if (mDVBStatus != null) {
            mDVBStatus.ageLocked(locked);
        }
    }

    /**
     * Update age locked layout.
     */
    public void updateChannelLocked(boolean locked) {
        if (mDVBStatus != null) {
            mDVBStatus.channelLocked(locked);
        }
    }

    public boolean isIpAndSomeOtherTunerType() {
        return ipAndSomeOtherTunerType;
    }

    public int getCurrentLiveRoute() {
        return mCurrentLiveRoute;
    }

    public int getLiveRouteSat() {
        return mLiveRouteSat;
    }

    public int getLiveRouteTer() {
        return mLiveRouteTer;
    }

    public int getLiveRouteCab() {
        return mLiveRouteCab;
    }

    public int getLiveRouteIp() {
        return mLiveRouteIp;
    }

    public int getPlaybackRouteIDMain() {
        return mPlaybackRouteIDMain;
    }

    public int getCurrentRecordRoute() {
        return mCurrentRecordRoute;
    }

    public ParentalManager getParentalManager() {
        return mParentalManager;
    }

    public TeletextSubtitleAudioManager getTeletextSubtitleAudioManager() {
        return mTeletextSubtitleAudioManager;
    }

    public void setDVBStatus(DVBStatus mDVBStatus) {
        this.mDVBStatus = mDVBStatus;
    }
}
