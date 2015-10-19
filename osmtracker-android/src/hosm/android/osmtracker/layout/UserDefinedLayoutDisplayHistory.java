package hosm.android.osmtracker.layout;

import java.util.HashMap;
import java.util.Stack;

import android.content.Context;
import android.view.ViewGroup;
import android.widget.LinearLayout;

public class UserDefinedLayoutDisplayHistory extends LinearLayout {

	@SuppressWarnings("unused")
	private static final String TAG = UserDefinedLayout.class.getSimpleName();
	private HashMap<String, ViewGroup> layouts = new HashMap<String, ViewGroup>();
	private Stack<String> layoutStack = new Stack<String>();

	public UserDefinedLayoutDisplayHistory(Context ctx) {
		super(ctx);
	}
	
	public void push(String s) {
		if (layouts.get(s) != null) {
			layoutStack.push(s);
			if (this.getChildCount() > 0) {
				this.removeAllViews();
			}
			this.addView(layouts.get(layoutStack.peek()));
		}
	}
	
	public String pop() {
		String out = layoutStack.pop();
		if (this.getChildCount() > 0) {
			this.removeAllViews();
		}
		this.addView(layouts.get(layoutStack.peek()));
		return out;
	}
	
	public int getStackSize() {
		return layoutStack.size();
	}
	
	@Override
	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		this.getChildAt(0).setEnabled(enabled);
	}


}