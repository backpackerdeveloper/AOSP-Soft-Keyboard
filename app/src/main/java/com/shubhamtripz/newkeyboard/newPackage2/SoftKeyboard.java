/*
 * Copyright (C) 2008-2009 The Android Open Source Project
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

package com.shubhamtripz.newkeyboard.newPackage2;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.InputType;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.Display;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.widget.Button;
import android.widget.LinearLayout;
import android.text.Spanned;
import android.view.textservice.TextInfo;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.shubhamtripz.newkeyboard.R;
import com.shubhamtripz.newkeyboard.adapter.EmojiAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;
    private InputMethodManager mInputMethodManager;
    private LatinKeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;

    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;

    private LatinKeyboard mCurKeyboard;
    private RecyclerView emojiRecyclerView;

    private String mWordSeparators;
    private KeyboardDragDelegate keyboardDragDelegate;

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mInputMethodManager = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        mWordSeparators = getResources().getString(R.string.word_separators);
        keyboardDragDelegate = new KeyboardDragDelegate(this, getWindow().getWindow());

    }

    /**
     * Returns the context object whose resources are adjusted to match the metrics of the display.
     *
     * Note that before {@link android.os.Build.VERSION_CODES#KITKAT}, there is no way to support
     * multi-display scenarios, so the context object will just return the IME context itself.
     *
     * With initiating multi-display APIs from {@link android.os.Build.VERSION_CODES#KITKAT}, the
     * context object has to return with re-creating the display context according the metrics
     * of the display in runtime.
     *
     * Starts from {@link android.os.Build.VERSION_CODES#S_V2}, the returning context object has
     * became to IME context self since it ends up capable of updating its resources internally.
     *
     * @see {@link Context#createDisplayContext(Display)}
     */
    @NonNull Context getDisplayContext() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            // createDisplayContext is not available.
            return this;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S_V2) {
            // IME context sources is now managed by WindowProviderService from Android 12L.
            return this;
        }
        // An issue in Q that non-activity components Resources / DisplayMetrics in
        // Context doesn't well updated when the IME window moving to external display.
        // Currently we do a workaround is to create new display context directly and re-init
        // keyboard layout with this context.
        final WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        return createDisplayContext(wm.getDefaultDisplay());
    }

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    @Override public void onInitializeInterface() {
        final Context displayContext = getDisplayContext();
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new LatinKeyboard(displayContext, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(displayContext, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(displayContext, R.xml.symbols_shift);
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    @Override
    public View onCreateInputView() {
        // Inflate the keyboard layout
        final LinearLayout keyboardParent = (LinearLayout) getLayoutInflater().inflate(
                R.layout.input, null);

        // Set up the draggable handle for the keyboard
        Button handle = keyboardParent.findViewById(R.id.handle);
        handle.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                return keyboardDragDelegate.onTouch(view, motionEvent);
            }
        });

        // Set up the Latin keyboard view
        mInputView = keyboardParent.findViewById(R.id.keyboard);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setPreviewEnabled(false);
        setLatinKeyboard(mQwertyKeyboard);

        // Set up RecyclerView for emoji selection
        RecyclerView emojiRecyclerView = keyboardParent.findViewById(R.id.emojiRecyclerView);
        emojiRecyclerView.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // Sample emoji list
        List<String> emojiList = new ArrayList<>(Arrays.asList(
                "😀", "😂", "😍", "😎", "😭", "😡", "👍", "🙏", "🎉", "❤️",
                "😢", "😱", "😜", "🤔", "🤗", "😴", "😇", "😏", "😲", "🙄",
                "🤐", "😮", "🤩", "😋", "🤤", "😷", "🤒", "😅", "💪", "🙌",
                "🤝", "💃", "🕺", "💔", "👀", "🤫", "🙃", "😤", "😖"
        ));

// Emoji to keyword mapping
        Map<String, List<String>> emojiMap = new HashMap<>();
        emojiMap.put("😀", Arrays.asList("happy", "smile", "joy"));
        emojiMap.put("😂", Arrays.asList("laugh", "haha", "funny"));
        emojiMap.put("😍", Arrays.asList("love", "heart", "romance"));
        emojiMap.put("😎", Arrays.asList("cool", "chill", "confident"));
        emojiMap.put("😭", Arrays.asList("cry", "sad", "tears"));
        emojiMap.put("😡", Arrays.asList("angry", "mad", "furious"));
        emojiMap.put("👍", Arrays.asList("thumbs up", "good", "approve"));
        emojiMap.put("🙏", Arrays.asList("pray", "please", "request"));
        emojiMap.put("🎉", Arrays.asList("party", "celebrate", "event"));
        emojiMap.put("❤️", Arrays.asList("love", "heart", "romantic"));
        emojiMap.put("😢", Arrays.asList("sad", "tearful", "grief"));
        emojiMap.put("😱", Arrays.asList("shock", "scared", "surprise"));
        emojiMap.put("😜", Arrays.asList("playful", "winky", "crazy"));
        emojiMap.put("🤔", Arrays.asList("thinking", "hmm", "question"));
        emojiMap.put("🤗", Arrays.asList("hug", "care", "friendly"));
        emojiMap.put("😴", Arrays.asList("sleep", "tired", "zzz"));
        emojiMap.put("😇", Arrays.asList("angel", "innocent", "pure"));
        emojiMap.put("😏", Arrays.asList("smirk", "sly", "flirty"));
        emojiMap.put("😲", Arrays.asList("shocked", "surprised", "amazed"));
        emojiMap.put("🙄", Arrays.asList("eye roll", "bored", "whatever"));
        emojiMap.put("🤐", Arrays.asList("silent", "zip", "secret"));
        emojiMap.put("😮", Arrays.asList("wow", "astonished", "open mouth"));
        emojiMap.put("🤩", Arrays.asList("starstruck", "excited", "amazed"));
        emojiMap.put("😋", Arrays.asList("yum", "delicious", "satisfied"));
        emojiMap.put("🤤", Arrays.asList("drool", "hungry", "crave"));
        emojiMap.put("😷", Arrays.asList("sick", "ill", "mask"));
        emojiMap.put("🤒", Arrays.asList("fever", "sick", "unwell"));
        emojiMap.put("😅", Arrays.asList("nervous", "sweating", "relief"));
        emojiMap.put("💪", Arrays.asList("strong", "power", "muscle"));
        emojiMap.put("🙌", Arrays.asList("praise", "celebrate", "hooray"));
        emojiMap.put("🤝", Arrays.asList("handshake", "agreement", "deal"));
        emojiMap.put("💃", Arrays.asList("dance", "celebrate", "fun"));
        emojiMap.put("🕺", Arrays.asList("dance", "groove", "party"));
        emojiMap.put("💔", Arrays.asList("heartbreak", "sad", "broken"));
        emojiMap.put("👀", Arrays.asList("eyes", "look", "watch"));
        emojiMap.put("🤫", Arrays.asList("shush", "quiet", "secret"));
        emojiMap.put("🙃", Arrays.asList("upside down", "playful", "awkward"));
        emojiMap.put("😤", Arrays.asList("frustrated", "angry", "furious"));
        emojiMap.put("😖", Arrays.asList("upset", "pain", "annoyed"));


        // Set up the adapter with the click listener to handle emoji input
        EmojiAdapter.OnEmojiClickListener listener = emoji -> {
            InputConnection inputConnection = getCurrentInputConnection();
            if (inputConnection != null) {
                // Get the current cursor position
                CharSequence currentText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0).text;
                int cursorPosition = inputConnection.getExtractedText(new ExtractedTextRequest(), 0).selectionStart;

                // Insert the emoji at the current cursor position
                inputConnection.commitText(currentText.subSequence(0, cursorPosition) + emoji + currentText.subSequence(cursorPosition, currentText.length()), 1);
            }
        };

        // Handle "Share" button click
        Button shareButton = keyboardParent.findViewById(R.id.share_btn);
        shareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                shareCurrentInput();
            }
        });

        // Initialize the adapter and RecyclerView
        EmojiAdapter emojiAdapter = new EmojiAdapter(emojiList, listener);
        emojiRecyclerView.setAdapter(emojiAdapter);

        // Add a repeating task to check text input changes for real-time emoji filtering
        checkInputTextAndUpdateEmojis(emojiList, emojiMap, emojiAdapter);

        return keyboardParent;
    }

    private void checkInputTextAndUpdateEmojis(List<String> emojiList, Map<String, List<String>> emojiMap, EmojiAdapter emojiAdapter) {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            ExtractedTextRequest request = new ExtractedTextRequest();

            // Create a handler associated with the main thread
            Handler mainHandler = new Handler(Looper.getMainLooper());

            // Start a background thread to continuously poll for text changes
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (true) {
                        ExtractedText extractedText = inputConnection.getExtractedText(request, 0);
                        if (extractedText != null) {
                            String inputText = extractedText.text.toString().toLowerCase();

                            // Use the handler to post the update on the main thread
                            mainHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    filterEmojiList(inputText, emojiList, emojiMap, emojiAdapter);
                                }
                            });
                        }

                        // Sleep for a short time before checking again (for real-time updates)
                        try {
                            Thread.sleep(500); // Check every half second
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }).start();
        }
    }



    private void filterEmojiList(String inputText, List<String> emojiList, Map<String, List<String>> emojiMap, EmojiAdapter emojiAdapter) {
        // Temporary list to store matching emojis
        List<String> filteredEmojiList = new ArrayList<>();

        // First, add emojis that match the input text (i.e., inputText matches tags)
        for (String emoji : emojiList) {
            List<String> tags = emojiMap.get(emoji);
            if (tags != null && tags.stream().anyMatch(tag -> inputText.contains(tag))) {
                filteredEmojiList.add(emoji);
            }
        }

        // Add the remaining emojis that don't match the input text
        for (String emoji : emojiList) {
            if (!filteredEmojiList.contains(emoji)) {
                filteredEmojiList.add(emoji);
            }
        }

        // Update the emoji list in the adapter and refresh the RecyclerView
        emojiList.clear();
        emojiList.addAll(filteredEmojiList);
        emojiAdapter.notifyDataSetChanged();
    }


    private void shareCurrentInput() {
        InputConnection inputConnection = getCurrentInputConnection();
        if (inputConnection != null) {
            // Get the current text in the input field
            ExtractedText extractedText = inputConnection.getExtractedText(new ExtractedTextRequest(), 0);
            String currentInput = (extractedText != null) ? extractedText.text.toString() : "";

            // Create an intent to share the input text and emojis
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, currentInput);

            // Start the intent for sharing to other apps
            Intent chooser = Intent.createChooser(shareIntent, "Share text via Bobble AI:");
            chooser.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);  // Ensure the intent can be started from a service
            startActivity(chooser);
        }
    }



    private void setLatinKeyboard(LatinKeyboard nextKeyboard) {
        final boolean shouldSupportLanguageSwitchKey =
                mInputMethodManager.shouldOfferSwitchingToNextInputMethod(getToken());
        nextKeyboard.setLanguageSwitchKeyVisibility(shouldSupportLanguageSwitchKey);
        mInputView.setKeyboard(nextKeyboard);
    }

    private static final String TAG = "SoftKeyboard";

    @Override
    public void onConfigureWindow(Window win, boolean isFullscreen, boolean isCandidatesOnly) {

        WindowManager.LayoutParams params = getWindow().getWindow().getAttributes();
        params.y = 0;
        params.x = 0;
        params.width = WindowManager.LayoutParams.MATCH_PARENT;

        Log.d(TAG,
                "onConfigureWindow() called with: win = [" + win + "], isFullscreen = [" + isFullscreen + "], isCandidatesOnly = [" + isCandidatesOnly + "]");
        getWindow().getWindow().setAttributes(params);
    }

    @Override
    public void onComputeInsets(Insets outInsets) {
        super.onComputeInsets(outInsets);

        if (mInputView != null) {
            int inputViewHeight = mInputView.getHeight();
            int navBarHeight = getNavBarHeight();
            int windowYOffset = 0;

            if (getWindow() != null && getWindow().getWindow() != null) {
                windowYOffset = getWindow().getWindow().getAttributes().y;
            }

            outInsets.contentTopInsets = inputViewHeight + navBarHeight + windowYOffset;
        } else {
            // Fallback if mInputView is null
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }


    }

    @Override
    public boolean onEvaluateFullscreenMode() {
        return false;
    }


    private int getNavBarHeight() {
        Resources resources = getResources();
        int resourceId = resources.getIdentifier("navigation_bar_height", "dimen", "android");
        if (resourceId > 0) {
            return resources.getDimensionPixelSize(resourceId);
        }
        return 0;
    }
    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(getDisplayContext());
        mCandidateView.setService(this);
        return mCandidateView;
    }
    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();

        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }

        mPredictionOn = false;
        mCompletionOn = false;
        mCompletions = null;

        // We are now going to initialize our state based on the type of
        // text being edited.
        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mCurKeyboard = mQwertyKeyboard;
                mPredictionOn = true;

                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mPredictionOn = false;
                }

                if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == InputType.TYPE_TEXT_VARIATION_URI
                        || variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    mPredictionOn = false;
                }

                if ((attribute.inputType & InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);
    }
    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    @Override public void onFinishInput() {
        super.onFinishInput();

        // Clear current composing text and candidates.
        mComposing.setLength(0);
        updateCandidates();

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        setCandidatesViewShown(false);

        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        setLatinKeyboard(mCurKeyboard);
        mInputView.closing();
        final InputMethodSubtype subtype = mInputMethodManager.getCurrentInputMethodSubtype();
        mInputView.setSubtypeOnSpaceKey(subtype);
    }
    @Override
    public void onCurrentInputMethodSubtypeChanged(InputMethodSubtype subtype) {
        mInputView.setSubtypeOnSpaceKey(subtype);
    }
    /**
     * Deal with the editor reporting movement of its cursor.
     */
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                            int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                || newSelEnd != candidatesEnd)) {
            mComposing.setLength(0);
            updateCandidates();
            InputConnection ic = getCurrentInputConnection();
            if (ic != null) {
                ic.finishComposingText();
            }
        }
    }
    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i = 0; i < completions.length; i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        boolean dead = false;
        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);
            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }

        onKey(c, null);

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                return false;

            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }

        return super.onKeyDown(keyCode, event);
    }
    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }
    /**
     * Helper function to commit any text being composed in to the editor.
     */
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
            updateCandidates();
        }
    }
    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null
                && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != InputType.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }
    // Implementation of KeyboardViewListener
    public void onKey(int primaryCode, int[] keyCodes) {
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            sendKey(primaryCode);
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            handleBackspace();
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_LANGUAGE_SWITCH) {
            handleLanguageSwitch();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                setLatinKeyboard(mQwertyKeyboard);
            } else {
                setLatinKeyboard(mSymbolsKeyboard);
                mSymbolsKeyboard.setShifted(false);
            }
        } else {
            handleCharacter(primaryCode, keyCodes);
        }
    }
    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }
    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }

    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }
    private void handleShift() {
        if (mInputView == null) {
            return;
        }

        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        } else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            setLatinKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            setLatinKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
    }

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            getCurrentInputConnection().commitText(
                    String.valueOf((char) primaryCode), 1);
        }
    }
    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }
    private IBinder getToken() {
        final Dialog dialog = getWindow();
        if (dialog == null) {
            return null;
        }
        final Window window = dialog.getWindow();
        if (window == null) {
            return null;
        }
        return window.getAttributes().token;
    }
    private void handleLanguageSwitch() {
        mInputMethodManager.switchToNextInputMethod(getToken(), false /* onlyCurrentIme */);
    }
    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        return separators.contains(String.valueOf((char)code));
    }
    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }

    public void swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }

    public void swipeLeft() {
        handleBackspace();
    }
    public void swipeDown() {
        handleClose();
    }
    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }
}