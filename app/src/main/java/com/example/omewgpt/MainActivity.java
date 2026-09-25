package com.example.myapplication;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.util.Base64;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;

import com.bumptech.glide.Glide;
import com.google.genai.Client;
import com.google.genai.types.Blob;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.Part;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.noties.markwon.Markwon;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private Client client;
    private final List<String> conversation = new ArrayList<>();

    // Includes the custom Omew cat personality!
    private final String SYSTEM_PROMPT = "You are Omew, an incredibly cute and helpful cat AI assistant! You love to say 'nyaaa' and act playfully. Explain things clearly, simply, and adorably. IMPORTANT: When providing step-by-step guides, timelines, or procedures, you MUST wrap them in XML format like this: <Sequence><Step title=\"Step Name\">Markdown Content here</Step></Sequence>.";

    private final OkHttpClient clientOkHttp = new OkHttpClient();
    private String currentSessionId = UUID.randomUUID().toString();

    // YOUR KEYS
    private final String SUPABASE_URL = "";
    private final String SUPABASE_KEY = "";
    private final String GEMINI_KEY = "";

    private SwitchCompat tempChatSwitch;
    private DrawerLayout drawerLayout;
    private LinearLayout chatContainer;
    private Markwon markwon;

    private LinearLayout welcomeScreen;
    private EditText messageInput;
    private Button sendButton;

    private LinearLayout pinnedContainer;
    private TextView pinnedText;

    private LinearLayout imagePreviewContainer;
    private ImageView imagePreview;
    private Uri selectedImageUri = null;

    private final ActivityResultLauncher<Intent> speechRecognizerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    ArrayList<String> data = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                    if (data != null && !data.isEmpty()) {
                        messageInput.append(data.get(0) + " ");
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> pickMediaLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    selectedImageUri = uri;
                    imagePreview.setImageURI(uri);
                    imagePreviewContainer.setVisibility(View.VISIBLE);
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        client = Client.builder().apiKey(GEMINI_KEY).build();
        markwon = Markwon.create(this);

        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            int left = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()).left;
            int top = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()).top;
            int right = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()).right;
            int bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime()).bottom;
            v.setPadding(left, top, right, bottom);
            return insets;
        });

        drawerLayout = findViewById(R.id.drawerLayout);
        chatContainer = findViewById(R.id.chatContainer);
        tempChatSwitch = findViewById(R.id.tempChatSwitch);
        welcomeScreen = findViewById(R.id.welcomeScreen);

        ImageButton menuButton = findViewById(R.id.menuButton);
        ImageButton newChatButton = findViewById(R.id.newChatButton);
        ImageButton attachButton = findViewById(R.id.attachButton);
        ImageButton micButton = findViewById(R.id.micButton);
        TextView titleText = findViewById(R.id.titleText);

        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);

        pinnedContainer = findViewById(R.id.pinnedContainer);
        pinnedText = findViewById(R.id.pinnedText);
        findViewById(R.id.unpinButton).setOnClickListener(v -> pinnedContainer.setVisibility(View.GONE));

        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        imagePreview = findViewById(R.id.imagePreview);
        findViewById(R.id.removeImageButton).setOnClickListener(v -> clearAttachment());

        attachButton.setOnClickListener(v -> pickMediaLauncher.launch("image/*"));

        micButton.setOnClickListener(v -> {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            try {
                speechRecognizerLauncher.launch(intent);
            } catch (Exception e) {
                Toast.makeText(this, "Voice typing not supported on this device", Toast.LENGTH_SHORT).show();
            }
        });

        menuButton.setOnClickListener(v -> {
            loadSidebarHistory();
            drawerLayout.openDrawer(GravityCompat.START);
        });

        newChatButton.setOnClickListener(v -> {
            conversation.clear();
            chatContainer.removeAllViews();
            messageInput.setText("");
            pinnedContainer.setVisibility(View.GONE);
            clearAttachment();
            welcomeScreen.setVisibility(View.VISIBLE);
            currentSessionId = UUID.randomUUID().toString();
        });

        tempChatSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            conversation.clear();
            chatContainer.removeAllViews();
            messageInput.setText("");
            pinnedContainer.setVisibility(View.GONE);
            clearAttachment();
            welcomeScreen.setVisibility(View.VISIBLE);
            currentSessionId = UUID.randomUUID().toString();

            if (isChecked) {
                titleText.setText("Temporary Chat");
                titleText.setTextColor(Color.parseColor("#AAAAAA"));
                addSystemMessage("Memory disabled. This chat won't appear in history.");
            } else {
                titleText.setText("OmewGPT");
                titleText.setTextColor(Color.WHITE);
                addSystemMessage("Memory enabled. Chat will be saved to cloud.");
            }
        });

        sendButton.setOnClickListener(v -> {
            String message = messageInput.getText().toString();

            if (!message.trim().isEmpty() || selectedImageUri != null) {

                welcomeScreen.setVisibility(View.GONE);

                View userView = getLayoutInflater().inflate(R.layout.user_message, chatContainer, false);
                TextView userMessageView = userView.findViewById(R.id.messageText);
                androidx.cardview.widget.CardView imageCard = userView.findViewById(R.id.messageImageCard);
                ImageView messageImage = userView.findViewById(R.id.messageImage);

                userMessageView.setText(message);
                if (message.trim().isEmpty()) {
                    userMessageView.setVisibility(View.GONE);
                }

                if (selectedImageUri != null) {
                    messageImage.setImageURI(selectedImageUri);
                    imageCard.setVisibility(View.VISIBLE);
                }

                userView.findViewById(R.id.btnCopyUser).setOnClickListener(view -> copyToClipboard(message));
                userView.findViewById(R.id.btnEdit).setOnClickListener(view -> messageInput.setText(message));

                chatContainer.addView(userView);
                scrollToBottom();
                messageInput.setText("");

                Uri currentUri = selectedImageUri;
                clearAttachment();

                String displayMessage = currentUri != null ? "[Image Attached]\n" + message : message;

                new Thread(() -> {
                    try {
                        String base64Image = "";
                        byte[] imageBytes = null;

                        if (currentUri != null) {
                            InputStream inputStream = getContentResolver().openInputStream(currentUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                            if (bitmap != null) {
                                float maxDim = 800f;
                                float scale = Math.min(maxDim / bitmap.getWidth(), maxDim / bitmap.getHeight());
                                Bitmap resized = scale < 1 ? Bitmap.createScaledBitmap(bitmap, (int) (bitmap.getWidth() * scale), (int) (bitmap.getHeight() * scale), true) : bitmap;

                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                resized.compress(Bitmap.CompressFormat.JPEG, 70, baos);
                                imageBytes = baos.toByteArray();

                                base64Image = Base64.encodeToString(imageBytes, Base64.NO_WRAP);
                            }
                        }

                        String conversationEntry = !base64Image.isEmpty() ? "<img src=\"" + base64Image + "\">\n" + message : message;
                        conversation.add("User: " + conversationEntry);

                        StringBuilder promptHistory = new StringBuilder();
                        for (String s : conversation) {
                            promptHistory.append(s.replaceAll("<img src=\".*?\">\n?", "[Image Attached] ")).append("\n");
                        }

                        String textPrompt = SYSTEM_PROMPT + "\n\n" + promptHistory.toString();

                        runOnUiThread(() -> {
                            View thinkingView = getLayoutInflater().inflate(R.layout.thinking_bubble, chatContainer, false);
                            thinkingView.setTag("thinkingContainer");

                            ImageView thinkingGif = thinkingView.findViewById(R.id.thinkingGif);
                            TextView thinkingText = thinkingView.findViewById(R.id.thinkingText);

                            Glide.with(MainActivity.this)
                                    .asGif()
                                    .load("https://media3.giphy.com/avatars/arisa0905m/4Sktt2kbFY8n.gif")
                                    .into(thinkingGif);

                            ObjectAnimator colorAnim = ObjectAnimator.ofInt(thinkingText, "textColor", Color.parseColor("#444444"), Color.parseColor("#FFFFFF"));
                            colorAnim.setDuration(800);
                            colorAnim.setEvaluator(new ArgbEvaluator());
                            colorAnim.setRepeatCount(ValueAnimator.INFINITE);
                            colorAnim.setRepeatMode(ValueAnimator.REVERSE);
                            colorAnim.start();

                            chatContainer.addView(thinkingView);
                            scrollToBottom();

                            List<String> thinkingStates = Arrays.asList("Analyzing request...", "Searching knowledge...", "Structuring answer...");
                            Timer timer = new Timer();
                            timer.scheduleAtFixedRate(new TimerTask() {
                                int tick = 0;
                                @Override
                                public void run() {
                                    runOnUiThread(() -> {
                                        if (thinkingView.getParent() != null) {
                                            thinkingText.setText(thinkingStates.get(tick % thinkingStates.size()));
                                            tick++;
                                        } else {
                                            timer.cancel();
                                            colorAnim.cancel();
                                        }
                                    });
                                }
                            }, 0, 1500);
                        });

                        String aiResponse;

                        if (imageBytes != null) {
                            Blob blob = Blob.builder()
                                    .data(imageBytes)
                                    .mimeType("image/jpeg")
                                    .build();

                            Part imagePart = Part.builder().inlineData(blob).build();
                            Part textPart = Part.builder().text(textPrompt).build();

                            Content content = Content.builder()
                                    .parts(Arrays.asList(imagePart, textPart))
                                    .build();

                            GenerateContentResponse response = client.models.generateContent("gemini-3.6-flash", content, null);
                            aiResponse = response.text() != null ? response.text() : "";
                        } else {
                            GenerateContentResponse response = client.models.generateContent("gemini-3.6-flash", textPrompt, null);
                            aiResponse = response.text() != null ? response.text() : "";
                        }

                        conversation.add("AI: " + aiResponse);
                        saveChatToDatabase(false);

                        final String finalAiResponse = aiResponse;

                        runOnUiThread(() -> {
                            View thinkingChild = null;
                            for (int i = 0; i < chatContainer.getChildCount(); i++) {
                                View child = chatContainer.getChildAt(i);
                                if ("thinkingContainer".equals(child.getTag())) {
                                    thinkingChild = child;
                                    break;
                                }
                            }
                            if (thinkingChild != null) {
                                chatContainer.removeView(thinkingChild);
                            }

                            View aiView = getLayoutInflater().inflate(R.layout.ai_message, chatContainer, false);
                            LinearLayout bubbleContainer = aiView.findViewById(R.id.bubbleContentContainer);
                            renderRichAIResponse(bubbleContainer, finalAiResponse);

                            aiView.findViewById(R.id.actionRow).setVisibility(View.VISIBLE);
                            aiView.findViewById(R.id.btnCopyAi).setOnClickListener(view -> copyToClipboard(finalAiResponse.replaceAll("<.*?>", "")));
                            aiView.findViewById(R.id.btnPin).setOnClickListener(view -> pinMessage(finalAiResponse.replaceAll("<.*?>", "")));
                            aiView.findViewById(R.id.btnRedo).setOnClickListener(view -> {
                                String lastPrompt = null;
                                for (int j = conversation.size() - 1; j >= 0; j--) {
                                    if (conversation.get(j).startsWith("User: ")) {
                                        lastPrompt = conversation.get(j).substring(6);
                                        break;
                                    }
                                }
                                if (lastPrompt != null) {
                                    String cleanPrompt = lastPrompt.replaceAll("<img src=\".*?\">\n?", "").replace("[Image Attached]\n", "");
                                    messageInput.setText(cleanPrompt);
                                    sendButton.performClick();
                                }
                            });

                            chatContainer.addView(aiView);
                            scrollToBottom();
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            View thinkingChild = null;
                            for (int i = 0; i < chatContainer.getChildCount(); i++) {
                                View child = chatContainer.getChildAt(i);
                                if ("thinkingContainer".equals(child.getTag())) {
                                    thinkingChild = child;
                                    break;
                                }
                            }
                            if (thinkingChild != null) {
                                chatContainer.removeView(thinkingChild);
                            }
                            addSystemMessage("Connection Error: " + e.getMessage());
                        });
                    }
                }).start();
            }
        });
    }

    private void clearAttachment() {
        selectedImageUri = null;
        imagePreviewContainer.setVisibility(View.GONE);
    }

    private void renderRichAIResponse(LinearLayout parentBubble, String rawText) {
        parentBubble.removeAllViews();

        String cleanedText = rawText.replace("<Sequence>", "").replace("</Sequence>", "");
        Pattern pattern = Pattern.compile("<Step title=\"(.*?)\">(.*?)</Step>", Pattern.DOTALL);
        Matcher matcher = pattern.matcher(cleanedText);

        int lastMatchEnd = 0;
        int stepCount = 1;

        while (matcher.find()) {
            String prefixText = cleanedText.substring(lastMatchEnd, matcher.start()).trim();
            if (!prefixText.isEmpty()) {
                TextView tv = new TextView(this);
                tv.setTextColor(Color.BLACK);
                tv.setTextSize(17f);
                markwon.setMarkdown(tv, prefixText);
                parentBubble.addView(tv);
            }

            View stepView = getLayoutInflater().inflate(R.layout.step_item, parentBubble, false);
            TextView stepNum = stepView.findViewById(R.id.stepNumber);
            TextView stepTitle = stepView.findViewById(R.id.stepTitle);
            TextView stepBody = stepView.findViewById(R.id.stepBody);

            stepNum.setText(String.valueOf(stepCount));
            stepTitle.setText(matcher.group(1) != null ? matcher.group(1) : "");
            markwon.setMarkdown(stepBody, matcher.group(2) != null ? matcher.group(2).trim() : "");

            parentBubble.addView(stepView);

            lastMatchEnd = matcher.end();
            stepCount++;
        }

        String suffixText = cleanedText.substring(lastMatchEnd).trim();
        if (!suffixText.isEmpty()) {
            TextView tv = new TextView(this);
            tv.setTextColor(Color.BLACK);
            tv.setTextSize(17f);
            markwon.setMarkdown(tv, suffixText);
            parentBubble.addView(tv);
        }
    }

    private void pinMessage(String text) {
        markwon.setMarkdown(pinnedText, text);
        pinnedContainer.setVisibility(View.VISIBLE);
    }

    private void copyToClipboard(String text) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("Copied Chat", text);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
    }

    private void addSystemMessage(String text) {
        runOnUiThread(() -> {
            View sysView = getLayoutInflater().inflate(R.layout.system_message, chatContainer, false);
            TextView sysText = sysView.findViewById(R.id.messageText);
            sysText.setText(text);
            chatContainer.addView(sysView);
            scrollToBottom();
        });
    }

    private void scrollToBottom() {
        ScrollView chatScroll = findViewById(R.id.chatScroll);
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void saveChatToDatabase(boolean isPinned) {
        if (tempChatSwitch.isChecked() || conversation.isEmpty()) return;

        new Thread(() -> {
            try {
                JSONArray jsonArray = new JSONArray();
                for (String msg : conversation) {
                    jsonArray.put(msg);
                }

                String firstMessage = conversation.get(0).replace("User: ", "")
                        .replaceAll("<img src=\".*?\">\n?", "")
                        .replace("[Image Attached]\n", "")
                        .trim();

                String title = firstMessage.length() > 30 ? firstMessage.substring(0, 30) + "..." : (firstMessage.isEmpty() ? "Image Upload" : firstMessage);

                JSONObject jsonObject = new JSONObject();
                jsonObject.put("session_id", currentSessionId);
                jsonObject.put("title", title);
                jsonObject.put("history", jsonArray);
                jsonObject.put("is_pinned", isPinned);

                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(jsonObject.toString(), mediaType);

                Request request = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/chat_sessions?on_conflict=session_id")
                        .post(body)
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .addHeader("Prefer", "resolution=merge-duplicates")
                        .build();

                clientOkHttp.newCall(request).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void togglePinStatus(String sessionId, boolean pinStatus) {
        new Thread(() -> {
            try {
                JSONObject jsonObject = new JSONObject();
                jsonObject.put("is_pinned", pinStatus);

                MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
                RequestBody body = RequestBody.create(jsonObject.toString(), mediaType);

                Request request = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/chat_sessions?session_id=eq." + sessionId)
                        .patch(body)
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .build();

                try (Response response = clientOkHttp.newCall(request).execute()) {
                    if (response.isSuccessful()) {
                        loadSidebarHistory();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadSidebarHistory() {
        LinearLayout historyContainer = findViewById(R.id.historyContainer);
        LinearLayout pinnedHistoryContainer = findViewById(R.id.pinnedHistoryContainer);
        TextView pinnedHeader = findViewById(R.id.pinnedHeader);
        View historySeparator = findViewById(R.id.historySeparator);

        new Thread(() -> {
            try {
                Request request = new Request.Builder()
                        .url(SUPABASE_URL + "/rest/v1/chat_sessions?select=session_id,title,history,is_pinned&order=created_at.desc")
                        .get()
                        .addHeader("apikey", SUPABASE_KEY)
                        .addHeader("Authorization", "Bearer " + SUPABASE_KEY)
                        .build();

                try (Response response = clientOkHttp.newCall(request).execute()) {
                    if (response.isSuccessful() && response.body() != null) {
                        String responseData = response.body().string();
                        JSONArray jsonArray = new JSONArray(responseData);

                        runOnUiThread(() -> {
                            historyContainer.removeAllViews();
                            pinnedHistoryContainer.removeAllViews();
                            boolean hasPinned = false;

                            try {
                                for (int i = 0; i < jsonArray.length(); i++) {
                                    JSONObject sessionObj = jsonArray.getJSONObject(i);
                                    String sessionId = sessionObj.getString("session_id");
                                    String title = sessionObj.getString("title");
                                    JSONArray historyArray = sessionObj.getJSONArray("history");
                                    boolean isPinned = sessionObj.optBoolean("is_pinned", false);

                                    View sidebarItem = getLayoutInflater().inflate(R.layout.sidebar_item, null);
                                    TextView titleText = sidebarItem.findViewById(R.id.sidebarTitle);
                                    ImageButton pinBtn = sidebarItem.findViewById(R.id.sidebarPinBtn);

                                    titleText.setText(title);

                                    if (isPinned) {
                                        pinBtn.setColorFilter(Color.parseColor("#6750A4"));
                                        hasPinned = true;
                                        pinnedHistoryContainer.addView(sidebarItem);
                                    } else {
                                        historyContainer.addView(sidebarItem);
                                    }

                                    titleText.setOnClickListener(v -> loadPastConversation(sessionId, historyArray));
                                    pinBtn.setOnClickListener(v -> togglePinStatus(sessionId, !isPinned));
                                }
                            } catch (Exception e) {
                                e.printStackTrace();
                            }

                            if (hasPinned) {
                                pinnedHeader.setVisibility(View.VISIBLE);
                                historySeparator.setVisibility(View.VISIBLE);
                                pinnedHistoryContainer.setVisibility(View.VISIBLE);
                            } else {
                                pinnedHeader.setVisibility(View.GONE);
                                historySeparator.setVisibility(View.GONE);
                                pinnedHistoryContainer.setVisibility(View.GONE);
                            }
                        });
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void loadPastConversation(String sessionId, JSONArray historyArray) {
        currentSessionId = sessionId;
        conversation.clear();
        chatContainer.removeAllViews();
        pinnedContainer.setVisibility(View.GONE);
        welcomeScreen.setVisibility(View.GONE);
        clearAttachment();

        try {
            for (int i = 0; i < historyArray.length(); i++) {
                String message = historyArray.getString(i);
                conversation.add(message);

                if (message.startsWith("User: ")) {
                    View userView = getLayoutInflater().inflate(R.layout.user_message, chatContainer, false);
                    TextView userMessageView = userView.findViewById(R.id.messageText);
                    androidx.cardview.widget.CardView imageCard = userView.findViewById(R.id.messageImageCard);
                    ImageView messageImage = userView.findViewById(R.id.messageImage);

                    String msgText = message.substring(6); // Remove "User: "

                    Matcher matcher = Pattern.compile("<img src=\"(.*?)\">\n?").matcher(msgText);
                    if (matcher.find()) {
                        String b64 = matcher.group(1);
                        try {
                            byte[] decodedString = Base64.decode(b64, Base64.DEFAULT);
                            Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                            messageImage.setImageBitmap(decodedByte);
                            imageCard.setVisibility(View.VISIBLE);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        msgText = matcher.replaceFirst("");
                    }

                    msgText = msgText.replace("[Image Attached]\n", "");

                    userMessageView.setText(msgText);
                    if (msgText.trim().isEmpty()) {
                        userMessageView.setVisibility(View.GONE);
                    }

                    String finalMsgText = msgText;
                    userView.findViewById(R.id.btnCopyUser).setOnClickListener(v -> copyToClipboard(finalMsgText));
                    userView.findViewById(R.id.btnEdit).setOnClickListener(v -> messageInput.setText(finalMsgText));

                    chatContainer.addView(userView);
                } else if (message.startsWith("AI: ")) {
                    View aiView = getLayoutInflater().inflate(R.layout.ai_message, chatContainer, false);
                    String msgText = message.substring(4); // Remove "AI: "

                    LinearLayout bubbleContainer = aiView.findViewById(R.id.bubbleContentContainer);
                    renderRichAIResponse(bubbleContainer, msgText);

                    aiView.findViewById(R.id.btnCopyAi).setOnClickListener(v -> copyToClipboard(msgText.replaceAll("<.*?>", "")));
                    aiView.findViewById(R.id.btnPin).setOnClickListener(v -> pinMessage(msgText.replaceAll("<.*?>", "")));
                    aiView.findViewById(R.id.btnRedo).setOnClickListener(v -> {
                        String lastPrompt = null;
                        for (int j = conversation.size() - 1; j >= 0; j--) {
                            if (conversation.get(j).startsWith("User: ")) {
                                lastPrompt = conversation.get(j).substring(6);
                                break;
                            }
                        }
                        if (lastPrompt != null) {
                            String cleanPrompt = lastPrompt.replaceAll("<img src=\".*?\">\n?", "").replace("[Image Attached]\n", "");
                            messageInput.setText(cleanPrompt);
                            sendButton.performClick();
                        }
                    });

                    chatContainer.addView(aiView);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        scrollToBottom();
    }
}