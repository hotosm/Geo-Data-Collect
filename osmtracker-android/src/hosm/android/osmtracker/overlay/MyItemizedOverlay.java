package hosm.android.osmtracker.overlay;

import hosm.android.osmtracker.R;

import java.util.ArrayList;

import org.osmdroid.DefaultResourceProxyImpl;
import org.osmdroid.api.IMapView;
import org.osmdroid.views.overlay.ItemizedOverlay;
import org.osmdroid.views.overlay.OverlayItem;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Point;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.Window;
import android.widget.TextView;

public class MyItemizedOverlay extends ItemizedOverlay<OverlayItem> {
    private ArrayList<OverlayItem> mOverlays = new ArrayList<OverlayItem>();
    private Context mContext;

    public MyItemizedOverlay(Drawable defaultMarker, Context context) {
        // super(boundCenterBottom(defaultMarker));
        super(defaultMarker, new DefaultResourceProxyImpl(context));
        mContext = context;
    }

    public void addOverlay(OverlayItem overlay) {
        mOverlays.add(overlay);
        populate();
    }

    @Override
    protected OverlayItem createItem(int i) {
        return mOverlays.get(i);
    }

    @Override
    public int size() {
        return mOverlays.size();
    }

    protected boolean onTap(int index) {
        OverlayItem item = mOverlays.get(index);

        Log.d("Title", item.getTitle());
        Log.d("Snippet", item.getSnippet());

         //set up dialog
        Dialog dialog = new Dialog(mContext);
        dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        dialog.setContentView(R.layout.custom_dialog);
        //dialog.setTitle("This is my custom dialog box");

        dialog.setCancelable(true);
        //there are a lot of settings, for dialog, check them all out!

        //set up text
        TextView map_popup_header = (TextView) dialog.findViewById(R.id.map_popup_header);
        map_popup_header.setText(item.getTitle());

        TextView map_popup_body = (TextView) dialog.findViewById(R.id.map_popup_body);
        map_popup_body.setText(item.getSnippet());

        //now that the dialog is set up, it's time to show it    
        dialog.show();

        return true;
    }

    @Override
    public boolean onSnapToItem(int arg0, int arg1, Point arg2, IMapView arg3) {
        return false;
    }

}