package hosm.android.osmtracker.util;

import hosm.android.osmtracker.activity.TrackLogger;
import hosm.android.osmtracker.layout.UserDefinedLayoutDisplayHistory;
import hosm.android.osmtracker.listener.StillImageOnClickListener;
import hosm.android.osmtracker.listener.TextNoteOnClickListener;
import hosm.android.osmtracker.listener.VoiceRecOnClickListener;
import hosm.android.osmtracker.service.resources.IconResolver;
import java.util.HashMap;
import org.xmlpull.v1.XmlPullParser;
import android.content.Context;
import android.content.res.Resources;
import android.view.ViewGroup;

public class UserDefinedLayoutReaderHistory {

	@SuppressWarnings("unused")
	private static final String TAG = UserDefinedLayoutReader.class.getSimpleName();
	private HashMap<String, ViewGroup> layouts = new HashMap<String, ViewGroup>();
	private XmlPullParser parser;
	private Context context;
	private UserDefinedLayoutDisplayHistory userDefinedLayout;
	private IconResolver iconResolver;
	private TextNoteOnClickListener textNoteOnClickListener;
	private VoiceRecOnClickListener voiceRecordOnClickListener;
	private StillImageOnClickListener stillImageOnClickListener;
	private Resources resources;
	private int orientation;
	private static final int ICON_POS_AUTO = 0;
	private static final int ICON_POS_TOP = 1;
	private static final int ICON_POS_RIGHT = 2;
	private static final int ICON_POS_BOTTOM = 3;
	private static final int ICON_POS_LEFT = 4;
	private int currentLayoutIconPos = UserDefinedLayoutReaderHistory.ICON_POS_AUTO;
	private long currentTrackId;
	

	public UserDefinedLayoutReaderHistory(UserDefinedLayoutDisplayHistory udl, Context c, TrackLogger activity, long trackId, XmlPullParser input, IconResolver ir) {
		parser = input;
		context = c;
		resources = context.getResources();
		userDefinedLayout = udl;
		iconResolver = ir;
		currentTrackId = trackId;
		orientation = resources.getConfiguration().orientation;
	}


}
