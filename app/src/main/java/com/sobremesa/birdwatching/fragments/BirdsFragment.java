package com.sobremesa.birdwatching.fragments;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import com.nostra13.universalimageloader.core.listener.PauseOnScrollListener;
import com.nostra13.universalimageloader.core.listener.SimpleImageLoadingListener;
import com.sobremesa.birdwatching.BAMApplication;
import com.sobremesa.birdwatching.BAMConstants;
import com.sobremesa.birdwatching.R;
import com.sobremesa.birdwatching.activities.MainActivity;
import com.sobremesa.birdwatching.adapters.BirdsAdapter;
import com.sobremesa.birdwatching.database.BirdImageTable;
import com.sobremesa.birdwatching.database.SightingTable;
import com.sobremesa.birdwatching.models.remote.RemoteBirdImage;
import com.sobremesa.birdwatching.models.remote.RemoteSighting;
import com.sobremesa.birdwatching.providers.BAMContentProvider;
import com.sobremesa.birdwatching.services.BirdImageService;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Address;
import android.location.Geocoder;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class BirdsFragment extends Fragment implements LoaderCallbacks<Cursor> {

	public static final String TAG = BirdsFragment.class.getSimpleName();

    private ArrayList<RemoteSighting> mBirds;
    private HashMap<String, RemoteSighting> mBirdMap;
    private HashMap<String, Integer> mBirdPositionMap;

	private CursorLoader mCusorLoader;

    private BirdsAdapter mBirdsAdapter;
    private GridView mBirdsGv;

    private AsyncTask<Void,Void,Void> mPopulateImagesTask;

    private final BroadcastReceiver mImageBirdReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(BAMConstants.RELOAD_BIRD_IMAGES_BROADCAST_ACTION)){

                ArrayList<RemoteSighting> birdsComputed = intent.getExtras().getParcelableArrayList(BAMConstants.RELOAD_BIRD_IMAGES_BROADCAST_EXTRA);
                boolean isUpdateGv = false;
                int currentGvFirstVisiblePosition = mBirdsGv.getFirstVisiblePosition();
                int currentGvLastVisiblePosition = mBirdsGv.getLastVisiblePosition();

                for( RemoteSighting birdComputed : birdsComputed )
                {
                    RemoteSighting bird = mBirdMap.get(birdComputed.getSciName());
                    Integer position = mBirdPositionMap.get(birdComputed.getSciName());

                    if( bird != null )
                    {
                        bird.setImages(birdComputed.getImages());

                        if( position != null )
                        {

                            if( position >= currentGvFirstVisiblePosition && position <= currentGvLastVisiblePosition )
                                isUpdateGv = true;
                        }
                    }
                }

                if( isUpdateGv ) {

                    BAMApplication.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {

                            mBirdsAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        }
    };


    public static final BirdsFragment newInstance() {
        BirdsFragment f = new BirdsFragment();

        Bundle args = f.getArguments();
        if (args == null) {
            args = new Bundle();
        }

        f.setArguments(args);

        return f;
    }


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

        mBirds = new ArrayList<RemoteSighting>();
        mBirdMap = new HashMap<String, RemoteSighting>();
        mBirdPositionMap = new HashMap<String, Integer>();

        mBirdsAdapter = new BirdsAdapter(getActivity(), mBirds);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_birds, container, false);

        mBirdsGv = (GridView)view.findViewById(R.id.bird_gv);
        mBirdsGv.setAdapter(mBirdsAdapter);


		return view;
	}
	
	@Override
	public void onStart() {
		super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(BAMConstants.RELOAD_BIRD_IMAGES_BROADCAST_ACTION);
        getActivity().registerReceiver(mImageBirdReceiver, filter);

		getLoaderManager().initLoader(0, null, this);
	}

    @Override
    public void onStop() {
        super.onStop();

        getActivity().unregisterReceiver(mImageBirdReceiver);

        if( mPopulateImagesTask != null )
        {
            mPopulateImagesTask.cancel(true);
            mPopulateImagesTask = null;
        }
    }

    @Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {
		mCusorLoader = new CursorLoader(getActivity(), BAMContentProvider.Uris.SIGHTINGS_GROUP_BY_BIRD_URI, SightingTable.ALL_COLUMNS, null, null, null);

		return mCusorLoader;
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, final Cursor cursor) {


        if( cursor != null )
        {

            if( cursor.getCount() > 0 ) {

                int i = 0;
                final Intent intent = new Intent(getActivity(), BirdImageService.class);
                intent.putParcelableArrayListExtra(BirdImageService.Extras.BIRDS, mBirds);
                intent.setAction(Intent.ACTION_SYNC);


                mBirds.clear();
                mBirdMap.clear();
                mBirdPositionMap.clear();

                for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                    RemoteSighting bird = new RemoteSighting(cursor);
                    mBirds.add(bird);
                    mBirdMap.put(bird.getSciName(), bird);
                    mBirdPositionMap.put(bird.getSciName(), i);

                    ++i;
                }

                // Update Grid (no images)
                mBirdsAdapter.notifyDataSetChanged();

                // Populate Existing Images
                if( mPopulateImagesTask != null )
                    mPopulateImagesTask.cancel(true);

                mPopulateImagesTask = new AsyncTask<Void,Void,Void>()
                {

                    @Override
                    protected Void doInBackground(Void... params) {
                        // Populate Existing Images
                        populateImagesToBirds();

                        return null;
                    }

                    @Override
                    protected void onPostExecute(Void aVoid) {
                        super.onPostExecute(aVoid);

                        // Update Grid (with images)
                        mBirdsAdapter.notifyDataSetChanged();

                        // Now fetch more images
                        getActivity().startService(intent);
                    }
                };

                // Before executing the task, make sure no service is running
                // To prevent concurrent bird image handling
                getActivity().stopService(intent);
                mPopulateImagesTask.execute();


                // Update Title
                MainActivity act = (MainActivity)getActivity();

                if( act != null )
                    act.setTitle(cursor.getCount() + " Spotted Birds (within 50 km)");
            }


        }


	}

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {

    }



    private void populateImagesToBirds()
    {

        for( RemoteSighting bird : mBirds )
        {
            populateBirdImages(bird);
        }
    }

    private void populateBirdImages(RemoteSighting bird)
    {
        ArrayList<RemoteBirdImage> birdImages = new ArrayList<RemoteBirdImage>();
        Cursor imageCursor = getActivity().getContentResolver().query(BAMContentProvider.Uris.BIRD_IMAGES_URI, BirdImageTable.ALL_COLUMNS, BirdImageTable.SCI_NAME + "=?", new String[] { bird.getSciName()}, null);

        if( imageCursor != null ) {
            for (imageCursor.moveToFirst(); !imageCursor.isAfterLast(); imageCursor.moveToNext()) {
                RemoteBirdImage image = new RemoteBirdImage(imageCursor);
                birdImages.add(image);
            }
        }

        imageCursor.close();

        bird.setImages(birdImages);
    }



}
