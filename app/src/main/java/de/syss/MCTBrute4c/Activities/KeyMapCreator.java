/*
 * Copyright 2013 Gerhard Klostermeier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.syss.MCTBrute4c.Activities;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.os.Handler;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils.TruncateAt;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;
import de.syss.MCTBrute4c.Common;
import de.syss.MCTBrute4c.MCReader;
import de.syss.MCTBrute4c.R;
import de.syss.MCTBrute4c.Activities.Preferences.Preference;

/**
 * Configure key map process and create key map.
 * This Activity should be called via startActivityForResult() with
 * an Intent containing the {@link #EXTRA_KEYS_DIR}.
 * The result codes are:
 * <ul>
 * <li>{@link Activity#RESULT_OK} - Everything is O.K. The key map can be
 * retrieved by calling {@link Common#getKeyMap()}.</li>
 * <li>1 - Directory from {@link #EXTRA_KEYS_DIR} does not
 * exist.</li>
 * <li>2 - No directory specified in Intent
 * ({@link #EXTRA_KEYS_DIR})</li>
 * <li>3 - External Storage is not read/writable. This error is
 * displayed to the user via Toast.</li>
 * <li>4 - Directory from {@link #EXTRA_KEYS_DIR} is null.</li>
 * </ul>
 * @author Gerhard Klostermeier
 */
public class KeyMapCreator extends BasicActivity {

    // Input parameters.
    /**
     * Path to a directory with key files. The files in the directory
     * are the files the user can choose from. This must be in the Intent.
     */
    public final static String EXTRA_KEYS_DIR =
            "de.syss.MifareClassicTool.Activity.KEYS_DIR";

    /**
     * A boolean value to enable (default) or disable the possibility for the
     * user to change the key mapping range.
     */
    public final static String EXTRA_SECTOR_CHOOSER =
            "de.syss.MifareClassicTool.Activity.SECTOR_CHOOSER";
    /**
     * An integer value that represents the number of the
     * first sector for the key mapping process.
     */
    public final static String EXTRA_SECTOR_CHOOSER_FROM =
            "de.syss.MifareClassicTool.Activity.SECTOR_CHOOSER_FROM";
    /**
     * An integer value that represents the number of the
     * last sector for the key mapping process.
     */
    public final static String EXTRA_SECTOR_CHOOSER_TO =
            "de.syss.MifareClassicTool.Activity.SECTOR_CHOOSER_TO";
    /**
     * The title of the activity. Optional.
     * e.g. "Map Keys to Sectors"
     */
    public final static String EXTRA_TITLE =
            "de.syss.MifareClassicTool.Activity.TITLE";
    /**
     * The text of the start key mapping button. Optional.
     * e.g. "Map Keys to Sectors"
     */
    public final static String EXTRA_BUTTON_TEXT =
            "de.syss.MifareClassicTool.Activity.BUTTON_TEXT";

    // Output parameters.
    // For later use.
//    public final static String EXTRA_KEY_MAP =
//            "de.syss.MifareClassicTool.Activity.KEY_MAP";


    // Sector count of the biggest Mifare Classic tag (4K Tag)
    public static final int MAX_SECTOR_COUNT = 40;
    // Block count of the biggest sector (4K Tag, Sector 32-39)
    public static final int MAX_BLOCK_COUNT_PER_SECTOR = 16;

    private static final String LOG_TAG =
            KeyMapCreator.class.getSimpleName();

    private static final int DEFAULT_SECTOR_RANGE_FROM = 0;
    private static final int DEFAULT_SECTOR_RANGE_TO = 15;

    private Button mCreateKeyMap;
    private LinearLayout mKeyFilesGroup;
    private TextView mSectorRange;
    private final Handler mHandler = new Handler();
    private int mProgressStatus;
    private ProgressBar mProgressBar;
    private boolean mIsCreatingKeyMap;
    private File mKeyDirPath;
    private int mFirstSector;
    private int mLastSector;

    SharedPreferences sharedPref;

    EditText increaseStartView,decreaseStartView;

    /**
     * Set layout, set the mapping range
     * and initialize some member variables.
     * @see #EXTRA_SECTOR_CHOOSER
     * @see #EXTRA_SECTOR_CHOOSER_FROM
     * @see #EXTRA_SECTOR_CHOOSER_TO
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_key_map);
        mCreateKeyMap = (Button) findViewById(R.id.buttonCreateKeyMap);
        mSectorRange = (TextView) findViewById(R.id.textViewCreateKeyMapFromTo);
        mKeyFilesGroup = (LinearLayout) findViewById(
                R.id.linearLayoutCreateKeyMapKeyFiles);
        mProgressBar = (ProgressBar) findViewById(R.id.progressBarCreateKeyMap);

        bruteInfo = (TextView)findViewById(R.id.bruteInfo);

        // Init. sector range.
        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_SECTOR_CHOOSER)) {
            Button changeSectorRange = (Button) findViewById(
                    R.id.buttonCreateKeyMapChangeRange);
            boolean value = intent.getBooleanExtra(EXTRA_SECTOR_CHOOSER, true);
            changeSectorRange.setEnabled(value);
        }
        boolean custom = false;
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String from = sharedPref.getString("default_mapping_range_from", "");
        String to = sharedPref.getString("default_mapping_range_to", "");
        // Are there default values?
        if (!from.equals("")) {
            custom = true;
        }
        if (!to.equals("")) {
            custom = true;
        }
        // Are there given values?
        if (intent.hasExtra(EXTRA_SECTOR_CHOOSER_FROM)) {
            from = "" + intent.getIntExtra(EXTRA_SECTOR_CHOOSER_FROM, 0);
            custom = true;
        }
        if (intent.hasExtra(EXTRA_SECTOR_CHOOSER_TO)) {
            to = "" + intent.getIntExtra(EXTRA_SECTOR_CHOOSER_TO, 15);
            custom = true;
        }
        if (custom) {
            mSectorRange.setText(from + " - " + to);
        }

        // Init. title and button text.
        if (intent.hasExtra(EXTRA_TITLE)) {
            setTitle(intent.getStringExtra(EXTRA_TITLE));
        }
        if (intent.hasExtra(EXTRA_BUTTON_TEXT)) {
            ((Button) findViewById(R.id.buttonCreateKeyMap)).setText(
                    intent.getStringExtra(EXTRA_BUTTON_TEXT));
        }

        increaseStartView=(EditText)findViewById(R.id.increaseStart);
        decreaseStartView=(EditText)findViewById(R.id.decreaseStart);
        onSingleThread(this.findViewById(R.id.radioButton));
    }

    /**
     * Cancel the mapping process and disable NFC foreground dispatch system.
     * This method is not called, if screen orientation changes.
     * @see Common#disableNfcForegroundDispatch(Activity)
     */
    @Override
    public void onPause() {
        super.onPause();
        mIsCreatingKeyMap = false;
    }

    /**
     * List files from the {@link #EXTRA_KEYS_DIR} and select the last used
     * ones if {@link Preference#SaveLastUsedKeyFiles} is enabled.
     */
    @Override
    public void onStart() {
        super.onStart();

        if (mKeyDirPath == null) {
            // Is there a key directory in the Intent?
            if (!getIntent().hasExtra(EXTRA_KEYS_DIR)) {
                setResult(2);
                finish();
                return;
            }
            String path = getIntent().getStringExtra(EXTRA_KEYS_DIR);
            // Is path null?
            if (path == null) {
                setResult(4);
                finish();
                return;
            }
            mKeyDirPath = new File(path);
        }

        // Is external storage writable?
        if (!Common.isExternalStorageWritableErrorToast(this)) {
            setResult(3);
            finish();
            return;
        }
        // Does the directory exist?
        if (!mKeyDirPath.exists()) {
            setResult(1);
            finish();
            return;
        }

        // List key files and select last used (if corresponding
        // setting is active).
        boolean selectLastUsedKeyFiles = Common.getPreferences().getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true);
        ArrayList<String> selectedFiles = null;
        if (selectLastUsedKeyFiles) {
            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
            // All previously selected key files are stored in one string
            // separated by "/".
            String selectedFilesChain = sharedPref.getString(
                    "last_used_key_files", null);
            if (selectedFilesChain != null) {
                selectedFiles = new ArrayList<String>(
                        Arrays.asList(selectedFilesChain.split("/")));
            }
        }
        File[] keyFiles = mKeyDirPath.listFiles();
        Arrays.sort(keyFiles);
        mKeyFilesGroup.removeAllViews();
        for(File f : keyFiles) {
            CheckBox c = new CheckBox(this);
            c.setText(f.getName());
            if (selectLastUsedKeyFiles && selectedFiles != null
                    && selectedFiles.contains(f.getName())) {
                // Select file.
                c.setChecked(true);
            }
            mKeyFilesGroup.addView(c);
        }
    }

    /**
     * Select all of the key files.
     * @param view The View object that triggered the method
     * (in this case the select all button).
     */
    public void onSelectAll(View view) {
        selectKeyFiles(true);
    }

    /**
     * Select none of the key files.
     * @param view The View object that triggered the method
     * (in this case the select none button).
     */
    public void onSelectNone(View view) {
        selectKeyFiles(false);
    }

    /**
     * Select or deselect all key files.
     * @param allOrNone True for selecting all, False for none.
     */
    private void selectKeyFiles(boolean allOrNone) {
        for (int i = 0; i < mKeyFilesGroup.getChildCount(); i++) {
            CheckBox c = (CheckBox) mKeyFilesGroup.getChildAt(i);
            c.setChecked(allOrNone);
        }
    }

    /**
     * Inform the worker thread from {@link #createKeyMap(MCReader, Context)}
     * to stop creating the key map. If the thread is already
     * informed or does not exists this button will finish the activity.
     * @param view The View object that triggered the method
     * (in this case the cancel button).
     * @see #createKeyMap(MCReader, Context)
     */
    public void onCancelCreateKeyMap(View view) {
        if (mIsCreatingKeyMap) {
            mIsCreatingKeyMap = false;
        } else {
            finish();
        }
    }

    /**
     * Create a key map and save it to
     * {@link Common#setKeyMap(android.util.SparseArray)}.
     * For doing so it uses other methods (
     * {@link #createKeyMap(MCReader, Context)},
     * {@link #keyMapCreated(MCReader)}).
     * If {@link Preference#SaveLastUsedKeyFiles} is active, this will also
     * save the selected key files.
     * @param view The View object that triggered the method
     * (in this case the map keys to sectors button).
     * @see #createKeyMap(MCReader, Context)
     * @see #keyMapCreated(MCReader)
     */
    public void onCreateKeyMap(View view) {
        boolean saveLastUsedKeyFiles = Common.getPreferences().getBoolean(
                Preference.SaveLastUsedKeyFiles.toString(), true);
        String lastSelectedKeyFiles = "";
        // Check for checked check boxes.
        ArrayList<String> fileNames = new ArrayList<String>();
        for (int i = 0; i < mKeyFilesGroup.getChildCount(); i++) {
            CheckBox c = (CheckBox) mKeyFilesGroup.getChildAt(i);
            if (c.isChecked()) {
                fileNames.add(c.getText().toString());
            }
        }
        if (fileNames.size() > 0) {
            // Check if key files still exists.
            ArrayList<File> keyFiles = new ArrayList<File>();
            for (String fileName : fileNames) {
                File keyFile = new File(mKeyDirPath, fileName);
                if (keyFile.exists()) {
                    // Add key file.
                    keyFiles.add(keyFile);
                    if (saveLastUsedKeyFiles) {
                        lastSelectedKeyFiles += fileName + "/";
                    }
                } else {
                    Log.d(LOG_TAG, "Key file "
                            + keyFile.getAbsolutePath()
                            + "doesn't exists anymore.");
                }
            }
            if (keyFiles.size() > 0) {
                // Save last selected key files as "/"-separated string
                // (if corresponding setting is active).
                if (saveLastUsedKeyFiles) {
                    SharedPreferences sharedPref = getPreferences(
                            Context.MODE_PRIVATE);
                    Editor e = sharedPref.edit();
                    e.putString("last_used_key_files",
                            lastSelectedKeyFiles.substring(
                                    0, lastSelectedKeyFiles.length() - 1));
                    e.apply();
                }

                // Create reader.
                MCReader reader = Common.checkForTagAndCreateReader(this);
                if (reader == null) {
                    return;
                }

                // Set key files.
                File[] keys = keyFiles.toArray(new File[keyFiles.size()]);
                if (!reader.setKeyFile(keys, this)) {
                    // Error.
                    reader.close();
                    return;
                }
                // Don't turn screen of while mapping.
                getWindow().addFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                // Get key map range.
                if (mSectorRange.getText().toString().equals(
                        getString(R.string.text_sector_range_all))) {
                    // Read all.
                    mFirstSector = 0;
                    mLastSector = reader.getSectorCount()-1;
                } else {
                    String[] fromAndTo = mSectorRange.getText()
                            .toString().split(" ");
                    mFirstSector = Integer.parseInt(fromAndTo[0]);
                    mLastSector = Integer.parseInt(fromAndTo[2]);
                }
                // Set map creation range.
                if (!reader.setMappingRange(
                        mFirstSector, mLastSector)) {
                    // Error.
                    Toast.makeText(this,
                            R.string.info_mapping_sector_out_of_range,
                            Toast.LENGTH_LONG).show();
                    reader.close();
                    return;
                }
                Common.setKeyMapRange(mFirstSector, mLastSector);
                // Init. GUI elements.
                mProgressStatus = -1;
                mProgressBar.setMax((mLastSector-mFirstSector)+1);
                mCreateKeyMap.setEnabled(false);
                mIsCreatingKeyMap = true;
                Toast.makeText(this, R.string.info_wait_key_map,
                        Toast.LENGTH_SHORT).show();
                // Read as much as possible with given key file.
                createKeyMap(reader, this);
            }
        }
    }

    String currentKey="";
    String currentKey2="";
    TextView bruteInfo;
    String hexChar="0123456789ABCDEF";
    int hexIndex[]={0,0,0,0,0,0,0,0,0,0,0,0};
    int hexIndex2[]={15,15,15,15,15,15,15,15,15,15,15,15};
    int thread1HandledCount=0,thread2HandledCount=0;
    boolean doubleThread=false;

    public void onSingleThread(View view){
        ((RadioButton)findViewById(R.id.radioButton2)).setChecked(false);
        ((LinearLayout)findViewById(R.id.decreaseLayout)).setVisibility(View.INVISIBLE);
        doubleThread=false;
        sharedPref = getPreferences(MODE_PRIVATE);
        String increaseStart= sharedPref.getString("increaseStart","");
        if(increaseStart.equals("")) increaseStartView.setText("000000000000");
        else increaseStartView.setText(increaseStart);

    }

    public void onDoubleThread(View view){
        ((RadioButton)findViewById(R.id.radioButton)).setChecked(false);
        ((LinearLayout)findViewById(R.id.decreaseLayout)).setVisibility(View.VISIBLE);
        doubleThread=true;
        sharedPref = getPreferences(MODE_PRIVATE);
        String decreaseStart= sharedPref.getString("decreaseStart","");
        if(decreaseStart.equals("")) decreaseStartView.setText("FFFFFFFFFFFF");
        else decreaseStartView.setText(decreaseStart);
    }

    public int[] setHexIndex(String hexString){
        int outputHexIndex[]=new int[12];
        for(int i=0;i<12;i++)
            outputHexIndex[i]=hexChar.indexOf(hexString.charAt(i));
        return outputHexIndex;
    }

    public void onBruteForce(View view){
        final int bfFirstSector,bfLastSector;

        // Create reader.
        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }

        // Don't turn screen of while mapping.
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Get key map range.
        if (mSectorRange.getText().toString().equals(
                getString(R.string.text_sector_range_all))) {
            // Read all.
            bfFirstSector = 0;
            bfLastSector = reader.getSectorCount()-1;
        } else {
            String[] fromAndTo = mSectorRange.getText()
                    .toString().split(" ");
            bfFirstSector = Integer.parseInt(fromAndTo[0]);
            bfLastSector = Integer.parseInt(fromAndTo[2]);
        }

        hexIndex=setHexIndex(increaseStartView.getText().toString());

        Toast.makeText(this,"Sector:"+bfFirstSector+" Start!",Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(true){
                    String tempStr="";
                    for(int i:hexIndex) tempStr +=Character.toString( hexChar.charAt(i));
                    currentKey = tempStr;
                    byte[] keyInByte= Common.hexStringToByteArray(currentKey);
                    if (reader.checkKey(bfFirstSector,keyInByte,KeyMapCreator.this)){
                        final Editor sharedEditor = sharedPref.edit();
                        sharedEditor.putString("Key",currentKey);
                        sharedEditor.apply();
                        break;
                    }
                    thread1HandledCount++;
                    hexIncrease(11);
                }
            }
        }).start();

        if (doubleThread) {
            hexIndex2=setHexIndex(decreaseStartView.getText().toString());
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        String tempStr = "";
                        for (int i : hexIndex2) tempStr += Character.toString(hexChar.charAt(i));
                        currentKey2 = tempStr;
                        byte[] keyInByte = Common.hexStringToByteArray(currentKey2);
                        if (reader.checkKey(bfFirstSector, keyInByte, KeyMapCreator.this)) {
                            SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
                            final Editor sharedEditor = sharedPref.edit();
                            sharedEditor.putString("Key2", currentKey2);
                            sharedEditor.apply();
                            break;
                        }
                        thread2HandledCount++;
                        hexDecrease(11);
                    }
                }
            }).start();
        }

        final Handler scheduleUpdateUI=new Handler();
        final Runnable updateUI = new Runnable() {
            @Override
            public void run() {
                double speed=(thread1HandledCount+thread2HandledCount)/0.05;
                bruteInfo.setText(currentKey+"\n"+currentKey2+"\nspeed : "+speed+" keys/sec");
                thread1HandledCount=0;
                thread2HandledCount=0;
                scheduleUpdateUI.postDelayed(this, 50);
            }
        };
        scheduleUpdateUI.post(updateUI);
    }

    public void onSaveState(View view){
        sharedPref = getPreferences(MODE_PRIVATE);
        final Editor sharedEditor = sharedPref.edit();
        sharedEditor.putString("increaseStart",currentKey);
        if(doubleThread) sharedEditor.putString("decreaseStart",currentKey2);
        sharedEditor.apply();
    }

    public void hexIncrease(int degree){
        if ((hexIndex[degree] +=1)==16){
            hexIndex[degree]=0;
            hexIncrease(degree-1);
        }
    }

    public void hexDecrease(int degree){
        if ((hexIndex2[degree] -=1)==-1){
            hexIndex2[degree]=15;
            hexDecrease(degree-1);
        }
    }

    /**
     * Triggered by {@link #onCreateKeyMap(View)} this
     * method starts a worker thread that first creates a key map and then
     * calls {@link #keyMapCreated(MCReader)}.
     * It also updates the progress bar in the UI thread.
     * @param reader A connected {@link MCReader}.
     * @see #onCreateKeyMap(View)
     * @see #keyMapCreated(MCReader)
     */
    private void createKeyMap(final MCReader reader, final Context context) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // Build key map parts and update the progress bar.
                while (mProgressStatus < mLastSector) {
                    mProgressStatus = reader.buildNextKeyMapPart();
                    if (mProgressStatus == -1 || !mIsCreatingKeyMap) {
                        // Error while building next key map part.
                        break;
                    }

                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mProgressBar.setProgress(
                                    (mProgressStatus - mFirstSector) + 1);
                        }
                    });
                }

                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        getWindow().clearFlags(
                                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                        mProgressBar.setProgress(0);
                        mCreateKeyMap.setEnabled(true);
                        reader.close();
                        if (mIsCreatingKeyMap && mProgressStatus != -1) {
                            keyMapCreated(reader);
                        } else {
                            // Error during key map creation.
                            Common.setKeyMap(null);
                            Common.setKeyMapRange(-1, -1);
                            Toast.makeText(context, R.string.info_key_map_error,
                                    Toast.LENGTH_LONG).show();
                        }
                        mIsCreatingKeyMap = false;
                    }
                });
            }
        }).start();
    }

    /**
     * Triggered by {@link #createKeyMap(MCReader, Context)}, this method
     * sets the result code to {@link Activity#RESULT_OK},
     * saves the created key map to
     * {@link Common#setKeyMap(android.util.SparseArray)}
     * and finishes this Activity.
     * @param reader A {@link MCReader}.
     * @see #createKeyMap(MCReader, Context)
     * @see #onCreateKeyMap(View)
     */
    private void keyMapCreated(MCReader reader) {
        // LOW: Return key map in intent.
        if (reader.getKeyMap().size() == 0) {
            Common.setKeyMap(null);
            // Error. No valid key found.
            Toast.makeText(this, R.string.info_no_key_found,
                    Toast.LENGTH_LONG).show();
        } else {
            Common.setKeyMap(reader.getKeyMap());
//            Intent intent = new Intent();
//            intent.putExtra(EXTRA_KEY_MAP, mMCReader);
//            setResult(Activity.RESULT_OK, intent);
            setResult(Activity.RESULT_OK);
            finish();
        }

    }

    /**
     * Show a dialog which lets the user choose the key mapping range.
     * If intended, save the mapping range as default
     * (using {@link #saveMappingRange(String, String)}).
     * @param view The View object that triggered the method
     * (in this case the change button).
     */
    public void onChangeSectorRange(View view) {
        // Build dialog elements.
        LinearLayout ll = new LinearLayout(this);
        LinearLayout llv = new LinearLayout(this);
        int pad = Common.dpToPx(10);
        llv.setPadding(pad, pad, pad, pad);
        llv.setOrientation(LinearLayout.VERTICAL);
        llv.setGravity(Gravity.CENTER);
        ll.setGravity(Gravity.CENTER);
        TextView tvFrom = new TextView(this);
        tvFrom.setText(getString(R.string.text_from) + ": ");
        tvFrom.setTextSize(18);
        TextView tvTo = new TextView(this);
        tvTo.setText(" " + getString(R.string.text_to) + ": ");
        tvTo.setTextSize(18);

        final CheckBox saveAsDefault = new CheckBox(this);
        saveAsDefault.setLayoutParams(new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        saveAsDefault.setText(R.string.action_save_as_default);
        saveAsDefault.setTextSize(18);
        tvFrom.setTextColor(saveAsDefault.getCurrentTextColor());
        tvTo.setTextColor(saveAsDefault.getCurrentTextColor());

        InputFilter[] f = new InputFilter[1];
        f[0] = new InputFilter.LengthFilter(2);
        final EditText from = new EditText(this);
        from.setEllipsize(TruncateAt.END);
        from.setMaxLines(1);
        from.setSingleLine();
        from.setInputType(InputType.TYPE_CLASS_NUMBER);
        from.setMinimumWidth(60);
        from.setFilters(f);
        from.setGravity(Gravity.CENTER_HORIZONTAL);
        final EditText to = new EditText(this);
        to.setEllipsize(TruncateAt.END);
        to.setMaxLines(1);
        to.setSingleLine();
        to.setInputType(InputType.TYPE_CLASS_NUMBER);
        to.setMinimumWidth(60);
        to.setFilters(f);
        to.setGravity(Gravity.CENTER_HORIZONTAL);

        ll.addView(tvFrom);
        ll.addView(from);
        ll.addView(tvTo);
        ll.addView(to);
        llv.addView(ll);
        llv.addView(saveAsDefault);
        final Toast err = Toast.makeText(this,
                R.string.info_invalid_range, Toast.LENGTH_LONG);
        // Build dialog and show him.
        new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_mapping_range_title)
            .setMessage(R.string.dialog_mapping_range)
            .setView(llv)
            .setPositiveButton(R.string.action_ok,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Read from x to y.
                    String txtFrom = "" + DEFAULT_SECTOR_RANGE_FROM;
                    String txtTo = "" + DEFAULT_SECTOR_RANGE_TO;
                    boolean noFrom = false;
                    if (!from.getText().toString().equals("")) {
                        txtFrom = from.getText().toString();
                    } else {
                        noFrom = true;
                    }
                    if (!to.getText().toString().equals("")) {
                        txtTo = to.getText().toString();
                    } else if (noFrom) {
                        // No values provided. Read all sectors.
                        mSectorRange.setText(
                                getString(R.string.text_sector_range_all));
                        if (saveAsDefault.isChecked()) {
                            saveMappingRange("", "");
                        }
                        return;
                    }
                    int intFrom = Integer.parseInt(txtFrom);
                    int intTo = Integer.parseInt(txtTo);
                    if (intFrom > intTo || intFrom < 0
                            || intTo > MAX_SECTOR_COUNT - 1) {
                        // Error.
                        err.show();
                    } else {
                        mSectorRange.setText(txtFrom + " - " + txtTo);
                        if (saveAsDefault.isChecked()) {
                            // Save as default.
                            saveMappingRange(txtFrom, txtTo);
                        }
                    }
                }
            })
            .setNeutralButton(R.string.action_read_all_sectors,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Read all sectors.
                    mSectorRange.setText(
                            getString(R.string.text_sector_range_all));
                    if (saveAsDefault.isChecked()) {
                        // Save as default.
                        saveMappingRange("", "");
                    }
                }
            })
            .setNegativeButton(R.string.action_cancel,
                    new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int whichButton) {
                    // Cancel dialog (do nothing).
                }
            }).show();
    }

    /**
     * Helper method to save the mapping rage as default.
     * @param from Start of the mapping range.
     * @param to End of the mapping range.
     */
    private void saveMappingRange(String from, String to) {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        Editor sharedEditor = sharedPref.edit();
        sharedEditor.putString("default_mapping_range_from", from);
        sharedEditor.putString("default_mapping_range_to", to);
        sharedEditor.apply();
    }
}
