package hosm.android.osmtracker.test.activity;

import hosm.android.osmtracker.activity.OpenStreetMapUpload;
import hosm.android.osmtracker.test.util.MockData;
import android.test.ActivityInstrumentationTestCase2;

public class OSMUploadTest extends ActivityInstrumentationTestCase2<OpenStreetMapUpload> {

	public OSMUploadTest() {
		super("hosm.android.osmtracker", OpenStreetMapUpload.class);
	}
	
	@Override
	protected void setUp() throws Exception {
		// MockData.mockBigTrack(getInstrumentation().getContext(), 2000, 2000);
	}
	
	public void test() {
		System.out.println("Test");
	}
}
