package net.umatoma.ted2transcript;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class TranscriptActivity extends AppCompatActivity {

    private static final String TAG = "TranscriptActivity";
    private static final String STATE_TRANSCRIPT = "stateTranscript";
    private static final String STATE_TALK_URL = "stateTalkUrl";

    private TextView transcriptTextView;
    private TextView talkUrlTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transcript);

        this.transcriptTextView = findViewById(R.id.transcriptTextView);
        this.talkUrlTextView = findViewById(R.id.talkUrlTextView);
        this.transcriptTextView.setText(getString(R.string.loading_transcript));

        if (savedInstanceState != null) {
            String transcript = savedInstanceState.getString(STATE_TRANSCRIPT);
            String talkUrl = savedInstanceState.getString(STATE_TALK_URL);
            this.setViewData(transcript, talkUrl);
        } else {
            String shortUrl = this.getShortUrlFromSharedText();
            if (shortUrl != null) {
                this.fetchTranscript(shortUrl, new FetchTranscriptCallback() {
                    @Override
                    public void onSuccess(String transcript, String talkUrl) {
                        TranscriptActivity.this.setViewData(transcript, talkUrl);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        e.printStackTrace();
                        TranscriptActivity.this.transcriptTextView.setText(e.getMessage());
                    }
                });
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_TRANSCRIPT, String.valueOf(this.transcriptTextView.getText()));
        outState.putString(STATE_TALK_URL, String.valueOf(this.talkUrlTextView.getText()));

        super.onSaveInstanceState(outState);
    }

    private void setViewData(String transcript, String talkUrl) {
        this.transcriptTextView.setText(transcript);
        this.talkUrlTextView.setText(talkUrl);
    }

    private String getSharedText() {
        Intent intent = getIntent();
        String action = intent.getAction();

        if (action.equals(Intent.ACTION_SEND)) {
            Bundle extras = intent.getExtras();
            if (extras != null) {
                CharSequence text = extras.getCharSequence(Intent.EXTRA_TEXT);
                if (text != null) {
                    return String.valueOf(text);
                }
            }
        }

        return null;
    }

    private String getShortUrlFromSharedText() {
        String text = this.getSharedText();
        if (text != null) {
            Pattern pattern = Pattern.compile("https://go\\.ted\\.com/\\w+");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String shortUrl = matcher.group(0);
                return shortUrl;
            }
        }
        return null;
    }

    private void fetchTranscript(String shortUrl, FetchTranscriptCallback fetchTranscriptCallback) {
        Handler handler = new Handler(Looper.getMainLooper());

        OkHttpClient httpClient = new OkHttpClient()
                .newBuilder()
                .followRedirects(false)
                .followSslRedirects(false)
                .build();

        Request request = new Request.Builder().url(shortUrl).build();
        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                handler.post(() -> fetchTranscriptCallback.onFailure(e));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String talkUrl = response.header("location");
                String transcriptUrl = Uri.parse(talkUrl)
                        .buildUpon()
                        .appendPath("transcript.json")
                        .appendQueryParameter("language", "en")
                        .build()
                        .toString();

                Request transcriptRequest = new Request.Builder().url(transcriptUrl).build();
                httpClient.newCall(transcriptRequest).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        handler.post(() -> fetchTranscriptCallback.onFailure(e));
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String responseBody = response.body().string();
                        try {
                            String transcript = TranscriptActivity.this.transcriptJsonToString(responseBody);
                            handler.post(() -> fetchTranscriptCallback.onSuccess(transcript, talkUrl));
                        } catch (JSONException e) {
                            handler.post(() -> fetchTranscriptCallback.onFailure(e));
                        }
                    }
                });
            }
        });
    }

    private String transcriptJsonToString(String jsonString) throws JSONException {
        StringBuilder stringBuilder = new StringBuilder();

        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray paragraphs = jsonObject.getJSONArray("paragraphs");
        for (int i = 0; i < paragraphs.length(); i++) {
            JSONObject paragraph = paragraphs.getJSONObject(i);

            JSONArray cues = paragraph.getJSONArray("cues");
            for (int j = 0; j < cues.length(); j++) {
                JSONObject cue = cues.getJSONObject(j);

                String text = cue.getString("text").replaceAll("\\n", " ");
                stringBuilder.append(text);
                stringBuilder.append(" ");
            }
            stringBuilder.append("\n");
            stringBuilder.append("\n");
        }

        return stringBuilder.toString();
    }

    private interface FetchTranscriptCallback {
        void onSuccess(String transcript, String talkUrl);

        void onFailure(Exception e);
    }
}
