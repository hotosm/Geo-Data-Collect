package hosm.android.osmtracker.activity;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.ResourceProxy;
import org.osmdroid.api.IMapController;
import org.osmdroid.contributor.util.constants.OpenStreetMapContributorConstants;
import org.osmdroid.tileprovider.tilesource.ITileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.OverlayItem;
import org.osmdroid.views.overlay.PathOverlay;
import org.osmdroid.views.overlay.SimpleLocationOverlay;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import hosm.android.osmtracker.OSMTracker;
import hosm.android.osmtracker.R;
import hosm.android.osmtracker.db.DataHelper;
import hosm.android.osmtracker.db.TrackContentProvider;
import hosm.android.osmtracker.db.TrackContentProvider.Schema;
import hosm.android.osmtracker.layout.GpsStatusRecord;
import hosm.android.osmtracker.layout.UserDefinedLayout;
import hosm.android.osmtracker.listener.SensorListener;
import hosm.android.osmtracker.overlay.MyItemizedOverlay;
import hosm.android.osmtracker.overlay.WayPointsOverlay;
import hosm.android.osmtracker.receiver.MediaButtonReceiver;
import hosm.android.osmtracker.service.gps.GPSLogger;
import hosm.android.osmtracker.service.gps.GPSLoggerServiceConnection;
import hosm.android.osmtracker.util.FileSystemUtils;
import hosm.android.osmtracker.util.ThemeValidator;
import hosm.android.osmtracker.view.TextNoteDialog;
import hosm.android.osmtracker.view.VoiceRecDialog;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.Settings;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnClickListener;
import android.widget.Toast;

/**
 * Main track logger activity. Communicate with the GPS service to display GPS
 * status, and allow user to record waypoints.
 * 
 * @author Nicolas Guillaumin
 * 
 */
public class TrackLogger extends Activity implements
		OpenStreetMapContributorConstants {
	static final String LOG_TAG = "HOSMCollect";
	private static final String TAG = TrackLogger.class.getSimpleName();
	private static final int REQCODE_IMAGE_CAPTURE = 0;
	private static final int REQCODE_GALLERY_CHOSEN = 1;
	public static final String STATE_IS_TRACKING = "isTracking";
	public static final String STATE_BUTTONS_ENABLED = "buttonsEnabled";
	private GPSLogger gpsLogger;
	private Intent gpsLoggerServiceIntent;
	private UserDefinedLayout mainLayout;
	private boolean checkGPSFlag = true;
	private File currentImageFile;
	private long currentTrackId;
	private ServiceConnection gpsLoggerConnection = new GPSLoggerServiceConnection(
			this);
	private SharedPreferences prefs = null;
	private boolean buttonsEnabled = false;
	public static final int DIALOG_TEXT_NOTE = 1;
	public static final int DIALOG_VOICE_RECORDING = 2;
	private SensorListener sensorListener;
	private AudioManager mAudioManager;
	private ComponentName mediaButtonReceiver;
	private static final String CURRENT_ZOOM = "currentZoom";
	private static final String CURRENT_SCROLL_X = "currentScrollX";
	private static final String CURRENT_SCROLL_Y = "currentScrollY";
	private static final String CURRENT_CENTER_TO_GPS_POS = "currentCenterToGpsPos";
	private static final String CURRENT_ZOOMED_TO_TRACK = "currentZoomedToTrack";
	private static final String LAST_ZOOM = "lastZoomLevel";
	private static final int DEFAULT_ZOOM = 16;
	private MapView osmView;
	private IMapController osmViewController;
	private SimpleLocationOverlay myLocationOverlay;
	private PathOverlay pathOverlay;
	private WayPointsOverlay wayPointsOverlay;
	private boolean centerToGpsPos = true;
	private boolean zoomedToTrackAlready = false;
	private GeoPoint currentPosition;
	private Integer lastTrackPointIdProcessed = null;
	private ContentObserver trackpointContentObserver;
	MyItemizedOverlay myItemizedOverlay = null;
	private List<String> list;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		// Get the track id to work with
		currentTrackId = getIntent().getExtras().getLong(Schema.COL_TRACK_ID);
		Log.v(TAG, "Starting for track id " + currentTrackId);

		gpsLoggerServiceIntent = new Intent(this, GPSLogger.class);
		gpsLoggerServiceIntent.putExtra(Schema.COL_TRACK_ID, currentTrackId);

		// Populate default preference values
		PreferenceManager.setDefaultValues(this, R.xml.preferences, false);

		// get shared preferences
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		// Set application theme according to user settings
		setTheme(getResources()
				.getIdentifier(
						ThemeValidator.getValidTheme(prefs, getResources()),
						null, null));

		super.onCreate(savedInstanceState);

		setContentView(R.layout.tracklogger);
		
		// set trackLogger to keepScreenOn depending on the user's preference
		// View trackLoggerView = findViewById(R.id.tracklogger_root);
		// trackLoggerView.setKeepScreenOn(prefs.getBoolean(OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON,
		// OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON));

		// we'll restore previous button state, GPSStatusRecord will enable all
		// buttons, as soon as there's a gps fix
		if (savedInstanceState != null) {
			buttonsEnabled = savedInstanceState.getBoolean(
					STATE_BUTTONS_ENABLED, false);
		}

		// create sensor listener
		sensorListener = new SensorListener();

		mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		mediaButtonReceiver = new ComponentName(this,
				MediaButtonReceiver.class.getName());

		// Initialize OSM view
		osmView = (MapView) findViewById(R.id.displaytrackmap_osmView);
		osmView.setMultiTouchControls(true); // pinch to zoom
		// we'll use osmView to define if the screen is always on or not
		osmView.setKeepScreenOn(prefs.getBoolean(
				OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON,
				OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON));
		osmViewController = osmView.getController();

        //check XML HOSM collect
      	list = new ArrayList<String>();
      	File file = Environment.getExternalStorageDirectory();
      	String loc = file.getAbsolutePath() + File.separator + "odk" + File.separator + "instances";
      	Log.d(LOG_TAG,"location xml : " + loc);
        File XMLFile = new File(loc);
        scanXML(XMLFile);
		
		// Check if there is a saved zoom level
		if (savedInstanceState != null) {
			osmViewController.setZoom(savedInstanceState.getInt(CURRENT_ZOOM,
					DEFAULT_ZOOM));
			osmView.scrollTo(savedInstanceState.getInt(CURRENT_SCROLL_X, 0),
					savedInstanceState.getInt(CURRENT_SCROLL_Y, 0));
			centerToGpsPos = savedInstanceState.getBoolean(
					CURRENT_CENTER_TO_GPS_POS, centerToGpsPos);
			zoomedToTrackAlready = savedInstanceState.getBoolean(
					CURRENT_ZOOMED_TO_TRACK, zoomedToTrackAlready);
		} else {
			// Try to get last zoom Level from Shared Preferences
			SharedPreferences settings = getPreferences(MODE_PRIVATE);
			osmViewController.setZoom(settings.getInt(LAST_ZOOM, DEFAULT_ZOOM));
		}

		selectTileSource();

		createOverlays();

		// Create content observer for trackpoints
		trackpointContentObserver = new ContentObserver(new Handler()) {
			@Override
			public void onChange(boolean selfChange) {
				pathChanged();
			}
		};

		// Register listeners for zoom buttons
		findViewById(R.id.displaytrackmap_imgZoomIn).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View v) {
						osmViewController.zoomIn();
					}
				});
		findViewById(R.id.displaytrackmap_imgZoomOut).setOnClickListener(
				new OnClickListener() {
					@Override
					public void onClick(View v) {
						osmViewController.zoomOut();
					}
				});
	}
	
	public void scanXML(File dir){
		String xmlPattern = ".xml";
		File[] listFile = dir.listFiles();
		if (listFile != null) {
			for (int i = 0; i < listFile.length; i++) {
				if (listFile[i].isDirectory()) {
		            scanXML(listFile[i]);
		        } else {
		            if (listFile[i].getName().endsWith(xmlPattern)){
		            	Log.d(LOG_TAG,"xml hosm collect : "+listFile[i].getName());
		            	String namefile = listFile[i].getName();
		            	String[] tagname = namefile.split("_");
		            	String taghead = tagname[0];
		            	taghead = taghead.replace(" ", "_");
		            	Log.d(LOG_TAG, "taghead : " + taghead);
		            	try {
		            		File fXmlFile = new File(dir + File.separator + listFile[i].getName());
		            		DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		            		DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		            		Document doc = dBuilder.parse(fXmlFile);
		            		doc.getDocumentElement().normalize();
		            		NodeList nList = doc.getElementsByTagName(taghead);
		            		for (int temp = 0; temp < nList.getLength(); temp++) {
		            			Node nNode = nList.item(temp);
		            			if (nNode.getNodeType() == Node.ELEMENT_NODE) {
		            				Element eElement = (Element) nNode;
		            				Log.d(LOG_TAG, "GPS : " + eElement.getElementsByTagName("gps").item(0).getTextContent());
		            				list.add(eElement.getElementsByTagName("gps").item(0).getTextContent());
		            				String name = eElement.getElementsByTagName("name").item(0).getTextContent();
		            				String addr_full = eElement.getElementsByTagName("addr_full").item(0).getTextContent();
		            				String GPS = eElement.getElementsByTagName("gps").item(0).getTextContent();
		            				String[] parts = GPS.split(" ");
		            		        double lat = Double.parseDouble(parts[0]);
		            		        double lon = Double.parseDouble(parts[1]);
		            		        Log.d(LOG_TAG, "lat : " + lat + ", lon : " + lon);
		            		        Drawable poiLocationDrawable = this.getResources().getDrawable(
		            		                R.drawable.pin_marker);
		            		        MyItemizedOverlay itemizedoverlayForPoI = new MyItemizedOverlay(
		            		        		poiLocationDrawable, this);
		            		        GeoPoint myPoint1 = new GeoPoint(lat, lon);
		            		        //myItemizedOverlay.addItem(myPoint1, "PoI", "PoI");
		            		        OverlayItem overlayitem2 = new OverlayItem(name, addr_full, myPoint1);
		            		        itemizedoverlayForPoI.addOverlay(overlayitem2);
		            		        osmView.getOverlays().add(itemizedoverlayForPoI);
		            			}
		            		}
		            	}catch (Exception e) {
		            		e.printStackTrace();
		                }
		            }
		        }
		    }
		}
	}
	
	public File searchForFileInExternalStorage(String filename) {
	    File storage = Environment.getExternalStorageDirectory();

	    return searchForFileInFolder(filename, storage);
	}

	public File searchForFileInFolder(String filename, File folder) {
	    File[] children = folder.listFiles();
	    File result;

	    for (File child : children) {
	        if (child.isDirectory()) {
	            result = searchForFileInFolder(filename, child);
	            if (result != null) {
	                return result;
	            }
	        } else {
	            if (child.getName().equals(filename)) {
	                return child;
	            }
	        }
	    }

	    return null;
	}

	public void selectTileSource() {
		String mapTile = prefs.getString(
				OSMTracker.Preferences.KEY_UI_MAP_TILE,
				OSMTracker.Preferences.VAL_UI_MAP_TILE_MAPNIK);
		osmView.setTileSource(selectMapTile(mapTile));
	}

	private ITileSource selectMapTile(String mapTile) {
		try {
			Field f = TileSourceFactory.class.getField(mapTile);
			return (ITileSource) f.get(null);
		} catch (Exception e) {
			Log.e(TAG, "Invalid tile source '" + mapTile + "'", e);
			return TileSourceFactory.MAPNIK;
		}
	}

	@Override
	protected void onResume() {

		setTitle(getResources().getString(R.string.tracklogger) + ": #"
				+ currentTrackId);
		/**
		 * // set trackLogger to keepScreenOn depending on the user's preference
		 * View trackLoggerView = findViewById(R.id.tracklogger_root);
		 * trackLoggerView
		 * .setKeepScreenOn(prefs.getBoolean(OSMTracker.Preferences
		 * .KEY_UI_DISPLAY_KEEP_ON,
		 * OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON));
		 **/

		// Fix to the user's preferred orientation (if any)
		String preferredOrientation = prefs.getString(
				OSMTracker.Preferences.KEY_UI_ORIENTATION,
				OSMTracker.Preferences.VAL_UI_ORIENTATION);
		if (preferredOrientation
				.equals(OSMTracker.Preferences.VAL_UI_ORIENTATION_PORTRAIT)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		} else if (preferredOrientation
				.equals(OSMTracker.Preferences.VAL_UI_ORIENTATION_LANDSCAPE)) {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		} else {
			this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		}

		// Try to inflate the buttons layout
		try {
			String userLayout = prefs.getString(
					OSMTracker.Preferences.KEY_UI_BUTTONS_LAYOUT,
					OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT);
			if (OSMTracker.Preferences.VAL_UI_BUTTONS_LAYOUT.equals(userLayout)) {
				// Using default buttons layout
				mainLayout = new UserDefinedLayout(this, currentTrackId, null);
			} else {
				// Using user buttons layout
				File layoutFile = new File(
						Environment.getExternalStorageDirectory(),
						prefs.getString(OSMTracker.Preferences.KEY_STORAGE_DIR,
								OSMTracker.Preferences.VAL_STORAGE_DIR)
								+ File.separator
								+ Preferences.LAYOUTS_SUBDIR
								+ File.separator + userLayout);
				mainLayout = new UserDefinedLayout(this, currentTrackId,
						layoutFile);
			}

			// ((ViewGroup)
			// findViewById(R.id.tracklogger_root)).removeAllViews();
			// ((ViewGroup)
			// findViewById(R.id.tracklogger_root)).addView(mainLayout);

		} catch (Exception e) {
			Log.e(TAG, "Error while inflating UserDefinedLayout", e);
			Toast.makeText(this, R.string.error_userlayout_parsing,
					Toast.LENGTH_SHORT).show();
		}

		// Check GPS status
		if (checkGPSFlag
				&& prefs.getBoolean(
						OSMTracker.Preferences.KEY_GPS_CHECKSTARTUP,
						OSMTracker.Preferences.VAL_GPS_CHECKSTARTUP)) {
			checkGPSProvider();
		}

		// Register GPS status update for upper controls
		((GpsStatusRecord) findViewById(R.id.gpsStatus)).requestLocationUpdates(true);

		// Start GPS Logger service
		startService(gpsLoggerServiceIntent);

		// Bind to GPS service.
		// We can't use BIND_AUTO_CREATE here, because when we'll ubound
		// later, we want to keep the service alive in background
		bindService(gpsLoggerServiceIntent, gpsLoggerConnection, 0);

		// connect the sensor listener
		sensorListener.register(this);

		setEnabledActionButtons(buttonsEnabled);
		if (!buttonsEnabled) {
			Toast.makeText(this, R.string.tracklogger_waiting_gps,
					Toast.LENGTH_LONG).show();
		}

		mAudioManager.registerMediaButtonEventReceiver(mediaButtonReceiver);

		osmView.setKeepScreenOn(prefs.getBoolean(
				OSMTracker.Preferences.KEY_UI_DISPLAY_KEEP_ON,
				OSMTracker.Preferences.VAL_UI_DISPLAY_KEEP_ON));
		getContentResolver().registerContentObserver(
				TrackContentProvider.trackPointsUri(currentTrackId), true,
				trackpointContentObserver);
		lastTrackPointIdProcessed = null;
		pathChanged();
		selectTileSource();

		super.onResume();
	}

	private void checkGPSProvider() {
		LocationManager lm = (LocationManager) getSystemService(LOCATION_SERVICE);
		if (!lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
			// GPS isn't enabled. Offer user to go enable it
			new AlertDialog.Builder(this)
					.setTitle(R.string.tracklogger_gps_disabled)
					.setIcon(android.R.drawable.ic_dialog_alert)
					.setMessage(
							getResources().getString(
									R.string.tracklogger_gps_disabled_hint))
					.setCancelable(true)
					.setPositiveButton(android.R.string.yes,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									startActivity(new Intent(
											Settings.ACTION_LOCATION_SOURCE_SETTINGS));
								}
							})
					.setNegativeButton(android.R.string.no,
							new DialogInterface.OnClickListener() {
								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									dialog.cancel();
								}
							}).create().show();
			checkGPSFlag = false;
		}
	}

	@Override
	protected void onPause() {
		((GpsStatusRecord) findViewById(R.id.gpsStatus))
				.requestLocationUpdates(false);
		if (gpsLogger != null) {
			if (!gpsLogger.isTracking()) {
				Log.v(TAG, "Service is not tracking, trying to stopService()");
				unbindService(gpsLoggerConnection);
				stopService(gpsLoggerServiceIntent);
			} else {
				unbindService(gpsLoggerConnection);
			}
		}

		if (sensorListener != null) {
			sensorListener.unregister();
		}

		mAudioManager.unregisterMediaButtonEventReceiver(mediaButtonReceiver);
		getContentResolver().unregisterContentObserver(
				trackpointContentObserver);
		pathOverlay.clearPath();
		super.onPause();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// Save the fact that we are currently tracking or not
		if (gpsLogger != null) {
			outState.putBoolean(STATE_IS_TRACKING, gpsLogger.isTracking());
		}
		outState.putBoolean(STATE_BUTTONS_ENABLED, buttonsEnabled);
		outState.putInt(CURRENT_ZOOM, osmView.getZoomLevel());
		outState.putInt(CURRENT_SCROLL_X, osmView.getScrollX());
		outState.putInt(CURRENT_SCROLL_Y, osmView.getScrollY());
		outState.putBoolean(CURRENT_CENTER_TO_GPS_POS, centerToGpsPos);
		outState.putBoolean(CURRENT_ZOOMED_TO_TRACK, zoomedToTrackAlready);
		super.onSaveInstanceState(outState);
		super.onSaveInstanceState(outState);
	}

	/**
	 * Called when GPS is disabled
	 */
	public void onGpsDisabled() {
		// GPS disabled. Grey all.
		setEnabledActionButtons(false);
	}

	/**
	 * Called when GPS is enabled
	 */
	public void onGpsEnabled() {
		// Buttons can be enabled
		if (gpsLogger != null && gpsLogger.isTracking()) {
			setEnabledActionButtons(true);
		}
	}

	/**
	 * Enable buttons associated to tracking
	 * 
	 * @param enabled
	 *            true to enable, false to disable
	 */
	public void setEnabledActionButtons(boolean enabled) {
		if (mainLayout != null) {
			buttonsEnabled = enabled;
			mainLayout.setEnabled(enabled);
		}
	}

	// Create options menu
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.tracklogger_menu, menu);
		return true;
	}

	// Manage options menu selections
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent i;
		int itemId = item.getItemId();
		if (itemId == R.id.tracklogger_menu_stoptracking) {
			// Start / Stop tracking
			if (gpsLogger.isTracking()) {
				Intent intent = new Intent(OSMTracker.INTENT_STOP_TRACKING);
				sendBroadcast(intent);
				((GpsStatusRecord) findViewById(R.id.gpsStatus))
						.manageRecordingIndicator(false);
				finish();
			}
			/**
			 * } else if (itemId == R.id.tracklogger_menu_settings) { // Start
			 * settings activity startActivity(new Intent(this,
			 * Preferences.class)); } else if (itemId ==
			 * R.id.tracklogger_menu_waypointlist) { // Start Waypoint list
			 * activity i = new Intent(this, WaypointList.class);
			 * i.putExtra(Schema.COL_TRACK_ID, currentTrackId);
			 * startActivity(i);
			 * 
			 * } else if (itemId == R.id.tracklogger_menu_about) { // Start
			 * About activity startActivity(new Intent(this, About.class)); }
			 * else if (itemId == R.id.tracklogger_menu_displaytrack) { // Start
			 * display track activity, with or without OSM background boolean
			 * useOpenStreetMapBackground =
			 * PreferenceManager.getDefaultSharedPreferences(this).getBoolean(
			 * OSMTracker.Preferences.KEY_UI_DISPLAYTRACK_OSM,
			 * OSMTracker.Preferences.VAL_UI_DISPLAYTRACK_OSM); if
			 * (useOpenStreetMapBackground) { i = new Intent(this,
			 * DisplayTrackMap.class); } else { i = new Intent(this,
			 * DisplayTrack.class); } i.putExtra(Schema.COL_TRACK_ID,
			 * currentTrackId); startActivity(i);
			 **/
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
			// Manage back button if we are on a sub-page
			if (event.getRepeatCount() == 0) {
				if (mainLayout != null && mainLayout.getStackSize() > 1) {
					mainLayout.pop();
					return true;
				}
			}
			break;
		case KeyEvent.KEYCODE_CAMERA:
			if (gpsLogger.isTracking()) {
				requestStillImage();
				return true;
			}
			break;
		case KeyEvent.KEYCODE_DPAD_CENTER:
			// API Level 3 doesn't support long presses, so we need to do this
			// manually
			if ((gpsLogger != null && gpsLogger.isTracking())
					&& (event.getEventTime() - event.getDownTime()) > OSMTracker.LONG_PRESS_TIME) {
				// new long press of dpad center detected, start voice recording
				// dialog
				this.showDialog(DIALOG_VOICE_RECORDING);
				return true;
			}
			break;
		case KeyEvent.KEYCODE_HEADSETHOOK:
			if (gpsLogger != null && gpsLogger.isTracking()) {
				this.showDialog(DIALOG_VOICE_RECORDING);
				return true;
			}
		}

		return super.onKeyDown(keyCode, event);
	}

	/**
	 * Request a still picture from the camera application, saving the file in
	 * the current track directory
	 */
	public void requestStillImage() {
		if (gpsLogger.isTracking()) {
			final File imageFile = pushImageFile();
			if (null != imageFile) {

				final String pictureSource = prefs.getString(
						OSMTracker.Preferences.KEY_UI_PICTURE_SOURCE,
						OSMTracker.Preferences.VAL_UI_PICTURE_SOURCE);
				if (OSMTracker.Preferences.VAL_UI_PICTURE_SOURCE_CAMERA
						.equals(pictureSource)) {
					startCamera(imageFile);
				} else if (OSMTracker.Preferences.VAL_UI_PICTURE_SOURCE_GALLERY
						.equals(pictureSource)) {
					startGallery();
				} else {
					// Let the user choose between using the camera
					// or selecting a picture from the gallery

					AlertDialog.Builder getImageFrom = new AlertDialog.Builder(
							TrackLogger.this);
					getImageFrom.setTitle("Select:");
					final CharSequence[] opsChars = {
							getString(R.string.tracklogger_camera),
							getString(R.string.tracklogger_gallery) };
					getImageFrom
							.setItems(
									opsChars,
									new android.content.DialogInterface.OnClickListener() {

										@Override
										public void onClick(
												DialogInterface dialog,
												int which) {
											if (which == 0) {
												startCamera(imageFile);
											} else if (which == 1) {
												startGallery();
											}
											dialog.dismiss();
										}
									});

					getImageFrom.show();
				}
			} else {
				Toast.makeText(
						getBaseContext(),
						getResources().getString(
								R.string.error_externalstorage_not_writable),
						Toast.LENGTH_SHORT).show();
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		Log.v(TAG, "Activity result: " + requestCode + ", resultCode="
				+ resultCode + ", Intent=" + data);
		switch (requestCode) {
		case REQCODE_IMAGE_CAPTURE:
			if (resultCode == RESULT_OK) {
				// A still image has been captured, track the corresponding
				// waypoint
				// Send an intent to inform service to track the waypoint.
				File imageFile = popImageFile();
				if (imageFile != null) {
					Intent intent = new Intent(OSMTracker.INTENT_TRACK_WP);
					intent.putExtra(Schema.COL_TRACK_ID, currentTrackId);
					intent.putExtra(OSMTracker.INTENT_KEY_NAME, getResources()
							.getString(R.string.wpt_stillimage));
					intent.putExtra(OSMTracker.INTENT_KEY_LINK,
							imageFile.getName());
					sendBroadcast(intent);
				}
			}
			break;
		case REQCODE_GALLERY_CHOSEN:
			if (resultCode == RESULT_OK) {
				// An image has been selected from the gallery, track the
				// corresponding waypoint
				File imageFile = popImageFile();
				if (imageFile != null) {
					// Copy the file from the gallery
					Cursor c = getContentResolver().query(data.getData(), null,
							null, null, null);
					c.moveToFirst();
					String f = c.getString(c.getColumnIndex(ImageColumns.DATA));
					c.close();
					Log.d(TAG, "Copying gallery file '" + f + "' into '"
							+ imageFile + "'");
					FileSystemUtils.copyFile(imageFile.getParentFile(),
							new File(f), imageFile.getName());

					// Send an intent to inform service to track the waypoint.
					Intent intent = new Intent(OSMTracker.INTENT_TRACK_WP);
					intent.putExtra(Schema.COL_TRACK_ID, currentTrackId);
					intent.putExtra(OSMTracker.INTENT_KEY_NAME, getResources()
							.getString(R.string.wpt_stillimage));
					intent.putExtra(OSMTracker.INTENT_KEY_LINK,
							imageFile.getName());
					sendBroadcast(intent);
				}
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	/**
	 * Getter for gpsLogger
	 * 
	 * @return Activity {@link GPSLogger}
	 */
	public GPSLogger getGpsLogger() {
		return gpsLogger;
	}

	/**
	 * Setter for gpsLogger
	 * 
	 * @param l
	 *            {@link GPSLogger} to set.
	 */
	public void setGpsLogger(GPSLogger l) {
		this.gpsLogger = l;
	}

	/**
	 * Gets a File for storing an image in the current track dir and stores it
	 * in a class variable.
	 * 
	 * @return A File pointing to an image file inside the current track
	 *         directory
	 */
	public File pushImageFile() {
		currentImageFile = null;

		// Query for current track directory
		File trackDir = DataHelper.getTrackDirectory(currentTrackId);

		// Create the track storage directory if it does not yet exist
		if (!trackDir.exists()) {
			if (!trackDir.mkdirs()) {
				Log.w(TAG, "Directory [" + trackDir.getAbsolutePath()
						+ "] does not exist and cannot be created");
			}
		}

		// Ensure that this location can be written to
		if (trackDir.exists() && trackDir.canWrite()) {
			currentImageFile = new File(trackDir,
					DataHelper.FILENAME_FORMATTER.format(new Date())
							+ DataHelper.EXTENSION_JPG);
		} else {
			Log.w(TAG, "The directory [" + trackDir.getAbsolutePath()
					+ "] will not allow files to be created");
		}

		return currentImageFile;
	}

	/**
	 * @return The current image file, and clear the internal variable.
	 */
	public File popImageFile() {
		File imageFile = currentImageFile;
		currentImageFile = null;
		return imageFile;
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_TEXT_NOTE:
			// create a new TextNoteDialog
			return new TextNoteDialog(this, currentTrackId);
		case DIALOG_VOICE_RECORDING:
			// create a new VoiceRegDialog
			return new VoiceRecDialog(this, currentTrackId);
		}
		return super.onCreateDialog(id);
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch (id) {
		case DIALOG_TEXT_NOTE:
			// we need to reset Values like uuid of the dialog,
			// otherwise we would overwrite an existing waypoint
			((TextNoteDialog) dialog).resetValues();
			break;
		}
		super.onPrepareDialog(id, dialog);
	}

	@Override
	protected void onStop() {
		super.onStop();
		SharedPreferences settings = getPreferences(MODE_PRIVATE);
		SharedPreferences.Editor editor = settings.edit();
		editor.putInt(LAST_ZOOM, osmView.getZoomLevel());
		editor.commit();
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.displaytrackmap_menu_center_to_gps).setEnabled(
				(!centerToGpsPos && currentPosition != null));
		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	protected void onNewIntent(Intent newIntent) {
		if (newIntent.getExtras() != null) {
			if (newIntent.getExtras().containsKey(Schema.COL_TRACK_ID)) {
				currentTrackId = newIntent.getExtras().getLong(
						Schema.COL_TRACK_ID);
				setIntent(newIntent);
			}
			if (newIntent.hasExtra("mediaButton") && gpsLogger != null
					&& gpsLogger.isTracking()) {
				this.showDialog(DIALOG_VOICE_RECORDING);
			}
		}
		super.onNewIntent(newIntent);
	}

	public long getCurrentTrackId() {
		return this.currentTrackId;
	}

	private void createOverlays() {
		pathOverlay = new PathOverlay(Color.BLUE, this);
		osmView.getOverlays().add(pathOverlay);

		myLocationOverlay = new SimpleLocationOverlay(this);
		osmView.getOverlays().add(myLocationOverlay);

		wayPointsOverlay = new WayPointsOverlay(this, currentTrackId);
		osmView.getOverlays().add(wayPointsOverlay);
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch (event.getAction()) {
		case MotionEvent.ACTION_MOVE:
			if (currentPosition != null)
				centerToGpsPos = false;
			break;
		}
		return super.onTouchEvent(event);
	}

	/**
	 * Starts the camera app. to take a picture
	 * 
	 * @param imageFile
	 *            File to save the picture to
	 */
	private void startCamera(File imageFile) {
		Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(imageFile));
		startActivityForResult(cameraIntent, REQCODE_IMAGE_CAPTURE);
	}

	/**
	 * Starts the gallery app. to choose a picture
	 */
	private void startGallery() {
		Intent intent = new Intent();
		intent.setType("image/*");
		intent.setAction(Intent.ACTION_GET_CONTENT);
		startActivityForResult(Intent.createChooser(intent,
				getString(R.string.tracklogger_choose_gallery_camera)),
				REQCODE_GALLERY_CHOSEN);
	}

	private void pathChanged() {
		if (isFinishing()) {
			return;
		}

		boolean doInitialBoundsCalc = false;
		double minLat = 91.0, minLon = 181.0;
		double maxLat = -91.0, maxLon = -181.0;
		if ((!zoomedToTrackAlready) && (lastTrackPointIdProcessed == null)) {
			final String[] proj_active = { Schema.COL_ACTIVE };
			Cursor cursor = getContentResolver().query(
					ContentUris.withAppendedId(
							TrackContentProvider.CONTENT_URI_TRACK,
							currentTrackId), proj_active, null, null, null);
			if (cursor.moveToFirst()) {
				doInitialBoundsCalc = (cursor.getInt(cursor
						.getColumnIndex(Schema.COL_ACTIVE)) == Schema.VAL_TRACK_INACTIVE);
			}
			cursor.close();
		}

		String[] projection = { Schema.COL_LATITUDE, Schema.COL_LONGITUDE,
				Schema.COL_ID };
		String selection = null;
		String[] selectionArgs = null;
		if (lastTrackPointIdProcessed != null) {
			selection = TrackContentProvider.Schema.COL_ID + " > ?";
			List<String> selectionArgsList = new ArrayList<String>();
			selectionArgsList.add(lastTrackPointIdProcessed.toString());

			selectionArgs = selectionArgsList.toArray(new String[1]);
		}

		// Retrieve any points we have not yet seen
		Cursor c = getContentResolver().query(
				TrackContentProvider.trackPointsUri(currentTrackId),
				projection, selection, selectionArgs, Schema.COL_ID + " asc");

		int numberOfPointsRetrieved = c.getCount();
		if (numberOfPointsRetrieved > 0) {
			c.moveToFirst();
			double lastLat = 0;
			double lastLon = 0;
			int primaryKeyColumnIndex = c.getColumnIndex(Schema.COL_ID);
			int latitudeColumnIndex = c.getColumnIndex(Schema.COL_LATITUDE);
			int longitudeColumnIndex = c.getColumnIndex(Schema.COL_LONGITUDE);

			// Add each new point to the track
			while (!c.isAfterLast()) {
				lastLat = c.getDouble(latitudeColumnIndex);
				lastLon = c.getDouble(longitudeColumnIndex);
				lastTrackPointIdProcessed = c.getInt(primaryKeyColumnIndex);
				pathOverlay.addPoint((int) (lastLat * 1e6),
						(int) (lastLon * 1e6));
				if (doInitialBoundsCalc) {
					if (lastLat < minLat)
						minLat = lastLat;
					if (lastLon < minLon)
						minLon = lastLon;
					if (lastLat > maxLat)
						maxLat = lastLat;
					if (lastLon > maxLon)
						maxLon = lastLon;
				}
				c.moveToNext();
			}

			// Last point is current position.
			currentPosition = new GeoPoint(lastLat, lastLon);
			myLocationOverlay.setLocation(currentPosition);
			if (centerToGpsPos) {
				osmViewController.setCenter(currentPosition);
			}

			// Repaint
			osmView.invalidate();
			if (doInitialBoundsCalc && (numberOfPointsRetrieved > 1)) {
				final double north = maxLat, east = maxLon, south = minLat, west = minLon;
				osmView.post(new Runnable() {
					@Override
					public void run() {
						osmViewController.zoomToSpan((int) (north - south),
								(int) (east - west));
						osmViewController.setCenter(new GeoPoint(
								(north + south) / 2, (east + west) / 2));
						zoomedToTrackAlready = true;
					}
				});
			}
		}
		c.close();
	}

}
