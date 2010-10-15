/*
 * Copyright (C) 2007 The Android Open Source Project
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

package android.widget;

import android.annotation.Widget;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.TypedArray;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * A view that displays one child at a time and lets the user pick among them.
 * The items in the Spinner come from the {@link Adapter} associated with
 * this view.
 *
 * <p>See the <a href="{@docRoot}resources/tutorials/views/hello-spinner.html">Spinner
 * tutorial</a>.</p>
 * 
 * @attr ref android.R.styleable#Spinner_prompt
 */
@Widget
public class Spinner extends AbsSpinner implements OnClickListener {
    private static final String TAG = "Spinner";
    
    /**
     * Use a dialog window for selecting spinner options.
     */
    public static final int MODE_DIALOG = 0;
    
    /**
     * Use a dropdown anchored to the Spinner for selecting spinner options.
     */
    public static final int MODE_DROPDOWN = 1;
    
    /**
     * Use the theme-supplied value to select the dropdown mode.
     */
    private static final int MODE_THEME = -1;
    
    private SpinnerPopup mPopup;
    private DropDownAdapter mTempAdapter;

    /**
     * Construct a new spinner with the given context's theme.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     */
    public Spinner(Context context) {
        this(context, null);
    }

    /**
     * Construct a new spinner with the given context's theme and the supplied
     * mode of displaying choices. <code>mode</code> may be one of
     * {@link #MODE_DIALOG} or {@link #MODE_DROPDOWN}.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     * @param mode Constant describing how the user will select choices from the spinner.
     * 
     * @see #MODE_DIALOG
     * @see #MODE_DROPDOWN
     */
    public Spinner(Context context, int mode) {
        this(context, null, com.android.internal.R.attr.spinnerStyle, mode);
    }

    /**
     * Construct a new spinner with the given context's theme and the supplied attribute set.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public Spinner(Context context, AttributeSet attrs) {
        this(context, attrs, com.android.internal.R.attr.spinnerStyle);
    }

    /**
     * Construct a new spinner with the given context's theme, the supplied attribute set,
     * and default style.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public Spinner(Context context, AttributeSet attrs, int defStyle) {
        this(context, attrs, defStyle, MODE_THEME);
    }

    /**
     * Construct a new spinner with the given context's theme, the supplied attribute set,
     * and default style. <code>mode</code> may be one of {@link #MODE_DIALOG} or
     * {@link #MODE_DROPDOWN} and determines how the user will select choices from the spinner.
     *
     * @param context The Context the view is running in, through which it can
     *        access the current theme, resources, etc.
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     * @param mode Constant describing how the user will select choices from the spinner.
     * 
     * @see #MODE_DIALOG
     * @see #MODE_DROPDOWN
     */
    public Spinner(Context context, AttributeSet attrs, int defStyle, int mode) {
        super(context, attrs, defStyle);

        TypedArray a = context.obtainStyledAttributes(attrs,
                com.android.internal.R.styleable.Spinner, defStyle, 0);

        if (mode == MODE_THEME) {
            mode = a.getInt(com.android.internal.R.styleable.Spinner_spinnerMode, MODE_DIALOG);
        }
        
        switch (mode) {
        case MODE_DIALOG: {
            mPopup = new DialogPopup();
            break;
        }

        case MODE_DROPDOWN: {
            final int hintResource = a.getResourceId(
                    com.android.internal.R.styleable.Spinner_popupPromptView, 0);

            DropdownPopup popup = new DropdownPopup(context, attrs, defStyle, hintResource);

            popup.setWidth(a.getDimensionPixelSize(
                    com.android.internal.R.styleable.Spinner_dropDownWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            popup.setBackgroundDrawable(a.getDrawable(
                    com.android.internal.R.styleable.Spinner_popupBackground));
            popup.setVerticalOffset(a.getDimensionPixelOffset(
                    com.android.internal.R.styleable.Spinner_dropDownVerticalOffset, 0));
            popup.setHorizontalOffset(a.getDimensionPixelOffset(
                    com.android.internal.R.styleable.Spinner_dropDownHorizontalOffset, 0));

            mPopup = popup;
            break;
        }
        }
        
        mPopup.setPromptText(a.getString(com.android.internal.R.styleable.Spinner_prompt));

        a.recycle();

        // Base constructor can call setAdapter before we initialize mPopup.
        // Finish setting things up if this happened.
        if (mTempAdapter != null) {
            mPopup.setAdapter(mTempAdapter);
            mTempAdapter = null;
        }
    }
    
    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        super.setAdapter(adapter);

        if (mPopup != null) {
            mPopup.setAdapter(new DropDownAdapter(adapter));
        } else {
            mTempAdapter = new DropDownAdapter(adapter);
        }
    }

    @Override
    public int getBaseline() {
        View child = null;

        if (getChildCount() > 0) {
            child = getChildAt(0);
        } else if (mAdapter != null && mAdapter.getCount() > 0) {
            child = makeAndAddView(0);
            // TODO: We should probably put the child in the recycler
        }

        if (child != null) {
            return child.getTop() + child.getBaseline();
        } else {
            return -1;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        
        if (mPopup != null && mPopup.isShowing()) {
            mPopup.dismiss();
        }
    }

    /**
     * <p>A spinner does not support item click events. Calling this method
     * will raise an exception.</p>
     *
     * @param l this listener will be ignored
     */
    @Override
    public void setOnItemClickListener(OnItemClickListener l) {
        throw new RuntimeException("setOnItemClickListener cannot be used with a spinner.");
    }

    /**
     * @see android.view.View#onLayout(boolean,int,int,int,int)
     *
     * Creates and positions all views
     *
     */
    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        mInLayout = true;
        layout(0, false);
        mInLayout = false;
    }

    /**
     * Creates and positions all views for this Spinner.
     *
     * @param delta Change in the selected position. +1 moves selection is moving to the right,
     * so views are scrolling to the left. -1 means selection is moving to the left.
     */
    @Override
    void layout(int delta, boolean animate) {
        int childrenLeft = mSpinnerPadding.left;
        int childrenWidth = mRight - mLeft - mSpinnerPadding.left - mSpinnerPadding.right;

        if (mDataChanged) {
            handleDataChanged();
        }

        // Handle the empty set by removing all views
        if (mItemCount == 0) {
            resetList();
            return;
        }

        if (mNextSelectedPosition >= 0) {
            setSelectedPositionInt(mNextSelectedPosition);
        }

        recycleAllViews();

        // Clear out old views
        removeAllViewsInLayout();

        // Make selected view and center it
        mFirstPosition = mSelectedPosition;
        View sel = makeAndAddView(mSelectedPosition);
        int width = sel.getMeasuredWidth();
        int selectedOffset = childrenLeft + (childrenWidth / 2) - (width / 2);
        sel.offsetLeftAndRight(selectedOffset);

        // Flush any cached views that did not get reused above
        mRecycler.clear();

        invalidate();

        checkSelectionChanged();

        mDataChanged = false;
        mNeedSync = false;
        setNextSelectedPositionInt(mSelectedPosition);
    }

    /**
     * Obtain a view, either by pulling an existing view from the recycler or
     * by getting a new one from the adapter. If we are animating, make sure
     * there is enough information in the view's layout parameters to animate
     * from the old to new positions.
     *
     * @param position Position in the spinner for the view to obtain
     * @return A view that has been added to the spinner
     */
    private View makeAndAddView(int position) {

        View child;

        if (!mDataChanged) {
            child = mRecycler.get(position);
            if (child != null) {
                // Position the view
                setUpChild(child);

                return child;
            }
        }

        // Nothing found in the recycler -- ask the adapter for a view
        child = mAdapter.getView(position, null, this);

        // Position the view
        setUpChild(child);

        return child;
    }

    /**
     * Helper for makeAndAddView to set the position of a view
     * and fill out its layout paramters.
     *
     * @param child The view to position
     */
    private void setUpChild(View child) {

        // Respect layout params that are already in the view. Otherwise
        // make some up...
        ViewGroup.LayoutParams lp = child.getLayoutParams();
        if (lp == null) {
            lp = generateDefaultLayoutParams();
        }

        addViewInLayout(child, 0, lp);

        child.setSelected(hasFocus());

        // Get measure specs
        int childHeightSpec = ViewGroup.getChildMeasureSpec(mHeightMeasureSpec,
                mSpinnerPadding.top + mSpinnerPadding.bottom, lp.height);
        int childWidthSpec = ViewGroup.getChildMeasureSpec(mWidthMeasureSpec,
                mSpinnerPadding.left + mSpinnerPadding.right, lp.width);

        // Measure child
        child.measure(childWidthSpec, childHeightSpec);

        int childLeft;
        int childRight;

        // Position vertically based on gravity setting
        int childTop = mSpinnerPadding.top
                + ((mMeasuredHeight - mSpinnerPadding.bottom - 
                        mSpinnerPadding.top - child.getMeasuredHeight()) / 2);
        int childBottom = childTop + child.getMeasuredHeight();

        int width = child.getMeasuredWidth();
        childLeft = 0;
        childRight = childLeft + width;

        child.layout(childLeft, childTop, childRight, childBottom);
    }

    @Override
    public boolean performClick() {
        boolean handled = super.performClick();
        
        if (!handled) {
            handled = true;

            if (!mPopup.isShowing()) {
                mPopup.show();
            }
        }

        return handled;
    }
    
    public void onClick(DialogInterface dialog, int which) {
        setSelection(which);
        dialog.dismiss();
    }

    /**
     * Sets the prompt to display when the dialog is shown.
     * @param prompt the prompt to set
     */
    public void setPrompt(CharSequence prompt) {
        mPopup.setPromptText(prompt);
    }

    /**
     * Sets the prompt to display when the dialog is shown.
     * @param promptId the resource ID of the prompt to display when the dialog is shown
     */
    public void setPromptId(int promptId) {
        setPrompt(getContext().getText(promptId));
    }

    /**
     * @return The prompt to display when the dialog is shown
     */
    public CharSequence getPrompt() {
        return mPopup.getHintText();
    }
    
    /**
     * <p>Wrapper class for an Adapter. Transforms the embedded Adapter instance
     * into a ListAdapter.</p>
     */
    private static class DropDownAdapter implements ListAdapter, SpinnerAdapter {
        private SpinnerAdapter mAdapter;
        private ListAdapter mListAdapter;

        /**
         * <p>Creates a new ListAdapter wrapper for the specified adapter.</p>
         *
         * @param adapter the Adapter to transform into a ListAdapter
         */
        public DropDownAdapter(SpinnerAdapter adapter) {
            this.mAdapter = adapter;
            if (adapter instanceof ListAdapter) {
                this.mListAdapter = (ListAdapter) adapter;
            }
        }

        public int getCount() {
            return mAdapter == null ? 0 : mAdapter.getCount();
        }

        public Object getItem(int position) {
            return mAdapter == null ? null : mAdapter.getItem(position);
        }

        public long getItemId(int position) {
            return mAdapter == null ? -1 : mAdapter.getItemId(position);
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            return getDropDownView(position, convertView, parent);
        }

        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return mAdapter == null ? null :
                    mAdapter.getDropDownView(position, convertView, parent);
        }

        public boolean hasStableIds() {
            return mAdapter != null && mAdapter.hasStableIds();
        }

        public void registerDataSetObserver(DataSetObserver observer) {
            if (mAdapter != null) {
                mAdapter.registerDataSetObserver(observer);
            }
        }

        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (mAdapter != null) {
                mAdapter.unregisterDataSetObserver(observer);
            }
        }

        /**
         * If the wrapped SpinnerAdapter is also a ListAdapter, delegate this call.
         * Otherwise, return true. 
         */
        public boolean areAllItemsEnabled() {
            final ListAdapter adapter = mListAdapter;
            if (adapter != null) {
                return adapter.areAllItemsEnabled();
            } else {
                return true;
            }
        }

        /**
         * If the wrapped SpinnerAdapter is also a ListAdapter, delegate this call.
         * Otherwise, return true.
         */
        public boolean isEnabled(int position) {
            final ListAdapter adapter = mListAdapter;
            if (adapter != null) {
                return adapter.isEnabled(position);
            } else {
                return true;
            }
        }

        public int getItemViewType(int position) {
            return 0;
        }

        public int getViewTypeCount() {
            return 1;
        }
        
        public boolean isEmpty() {
            return getCount() == 0;
        }
    }
    
    /**
     * Implements some sort of popup selection interface for selecting a spinner option.
     * Allows for different spinner modes.
     */
    private interface SpinnerPopup {
        public void setAdapter(ListAdapter adapter);
        
        /**
         * Show the popup
         */
        public void show();
        
        /**
         * Dismiss the popup
         */
        public void dismiss();
        
        /**
         * @return true if the popup is showing, false otherwise.
         */
        public boolean isShowing();
        
        /**
         * Set hint text to be displayed to the user. This should provide
         * a description of the choice being made.
         * @param hintText Hint text to set.
         */
        public void setPromptText(CharSequence hintText);
        public CharSequence getHintText();
    }
    
    private class DialogPopup implements SpinnerPopup, DialogInterface.OnClickListener {
        private AlertDialog mPopup;
        private ListAdapter mListAdapter;
        private CharSequence mPrompt;

        public void dismiss() {
            mPopup.dismiss();
            mPopup = null;
        }

        public boolean isShowing() {
            return mPopup != null ? mPopup.isShowing() : false;
        }

        public void setAdapter(ListAdapter adapter) {
            mListAdapter = adapter;
        }

        public void setPromptText(CharSequence hintText) {
            mPrompt = hintText;
        }
        
        public CharSequence getHintText() {
            return mPrompt;
        }

        public void show() {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            if (mPrompt != null) {
                builder.setTitle(mPrompt);
            }
            mPopup = builder.setSingleChoiceItems(mListAdapter,
                    getSelectedItemPosition(), this).show();
        }
        
        public void onClick(DialogInterface dialog, int which) {
            setSelection(which);
            dismiss();
        }
    }
    
    private class DropdownPopup extends ListPopupWindow implements SpinnerPopup {
        private CharSequence mHintText;
        private TextView mHintView;
        private int mHintResource;
        
        public DropdownPopup(Context context, AttributeSet attrs,
                int defStyleRes, int hintResource) {
            super(context, attrs, 0, defStyleRes);
            
            mHintResource = hintResource;
            
            setAnchorView(Spinner.this);
            setModal(true);
            setPromptPosition(POSITION_PROMPT_ABOVE);
            setOnItemClickListener(new OnItemClickListener() {
                public void onItemClick(AdapterView parent, View v, int position, long id) {
                    Spinner.this.setSelection(position);
                    dismiss();
                }
            });
        }
        
        public CharSequence getHintText() {
            return mHintText;
        }
        
        public void setPromptText(CharSequence hintText) {
            mHintText = hintText;
            if (mHintView != null) {
                mHintView.setText(hintText);
            }
        }

        @Override
        public void show() {
            if (mHintView == null) {
                final TextView textView = (TextView) LayoutInflater.from(getContext()).inflate(
                        mHintResource, null).findViewById(com.android.internal.R.id.text1);
                textView.setText(mHintText);
                setPromptView(textView);
                mHintView = textView;
            }
            super.show();
            getListView().setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            setSelection(Spinner.this.getSelectedItemPosition());
        }
    }
}
