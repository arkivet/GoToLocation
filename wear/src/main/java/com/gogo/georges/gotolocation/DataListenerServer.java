package com.gogo.georges.gotolocation;

import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.LocalBroadcastManager;

import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.List;

/**
 * Created by Georges on 2015-11-27.
 */
public class DataListenerServer extends WearableListenerService implements DataApi.DataListener
{
    private String wearPath = "/wear-path";
    private double destances = -1;
    private double bearingData = 00;

    @Override
    public void onDataChanged(DataEventBuffer dataEvents)
    {
        super.onDataChanged(dataEvents);
        final List<DataEvent> event = FreezableUtils.freezeIterable(dataEvents);
        for (DataEvent eventIter : event)
        {
            if (eventIter.getType() == DataEvent.TYPE_CHANGED)
            {
                final Uri uri = eventIter.getDataItem().getUri();
                final String path = uri != null ? uri.getPath() : null;

                if (this.wearPath.equals(path))
                {
                    final DataMap map = DataMapItem.fromDataItem(eventIter.getDataItem()).getDataMap();
                    // read your values
                    destances = map.getDouble("dest");
                    bearingData = map.getDouble("bear");

                    Intent startIntent = new Intent();
                    startIntent.setAction(Intent.ACTION_SEND);
                    startIntent.putExtra("destan", this.destances);
                    startIntent.putExtra("beard", this.bearingData);
                    LocalBroadcastManager.getInstance(this).sendBroadcast(startIntent);

                }



            }
        }
    }

}
