package com.example.popularmovies;

import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.popularmovies.data.FavoritesProvider;
import com.example.popularmovies.utilities.NetworkUtils;
import com.squareup.picasso.Picasso;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;

public class DetailActivity extends AppCompatActivity {

    private ScrollView mDetailScrollView;
    private ImageView mPosterView;
    private TextView mTitleView;
    private TextView mDescriptionView;
    private TextView mRatingView;
    private TextView mReleaseDateView;
    private Button mFavoriteButton;
    private Button mShareButton;

    private LinearLayout mTrailerList;
    private LinearLayout mReviewList;

    private String mPoster;
    private String mTitle;
    private String mDescription;
    private double mRating;
    private String mId;
    private String mReleaseDate;

    /* URL components for recovering the poster images */
    private final String POSTER_BASE_URL = "http://image.tmdb.org/t/p/";
    private final String POSTER_SIZE = "w155/";

    private final String TRAILER_BASE_URL = "http://youtube.com/watch?v=";
    private final String PARAM_RESULTS = "results";
    private final String PARAM_KEY = "key";
    private final String PARAM_NAME = "name";

    private final String PARAM_AUTHOR = "author";
    private final String PARAM_CONTENT = "content";

    private String[] mTrailerKeys;
    private String[] mTrailerNames;
    private String[] mReviewAuthors;
    private String[] mReviewContent;

    private int reviewCounter;

    private final String xCoord = "X-Coordinates";
    private final String yCoord = "Y-Coordinates";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        mDetailScrollView = findViewById(R.id.detail_scroll_view);
        mPosterView = findViewById(R.id.poster_image);
        mTitleView = findViewById(R.id.title_view);
        mDescriptionView = findViewById(R.id.description_text);
        mRatingView = findViewById(R.id.rating_text);
        mReleaseDateView = findViewById(R.id.date_text);
        mFavoriteButton = findViewById(R.id.favorite_button);
        mShareButton = findViewById(R.id.share_button);

        mTrailerList = findViewById(R.id.trailer_list);
        mReviewList = findViewById(R.id.review_list);

        reviewCounter = 0;

        /* Extract movie data through MainActivity Intent */
        Intent mainIntent = getIntent();
        Bundle movieDetails = mainIntent.getExtras();
        if (movieDetails != null) {
            mPoster = movieDetails.getString(getString(R.string.poster_url));
            mTitle = movieDetails.getString(getString(R.string.title));
            mDescription = movieDetails.getString(getString(R.string.description));
            mRating = movieDetails.getDouble(getString(R.string.rating));
            mReleaseDate = movieDetails.getString(getString(R.string.release_date));
            mId = movieDetails.getString(getString(R.string.movie_id));
        }

        populateUI();
        new FetchTrailersTask().execute();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putInt(xCoord, mDetailScrollView.getScrollX());
        outState.putInt(yCoord, mDetailScrollView.getScrollY());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        if (savedInstanceState != null) {
            mDetailScrollView.scrollTo(savedInstanceState.getInt(xCoord),
                    savedInstanceState.getInt(yCoord));
        }
    }

    private void populateUI() {
        final String mPosterUrl = POSTER_BASE_URL + POSTER_SIZE + mPoster;
        Picasso
                .with(this)
                .load(mPosterUrl)
                .placeholder(R.drawable.ic_launcher_background)
                .error(R.drawable.ic_launcher_background)
                .into(mPosterView);
        mPosterView.setAdjustViewBounds(true);

        mTitleView.setText(mTitle);
        mDescriptionView.setText(mDescription);
        mRatingView.setText(getString(R.string.out_of_ten, mRating));
        mReleaseDateView.setText(mReleaseDate.substring(0, 4));

        if (favoriteExists(mId))
        {
            mFavoriteButton.setTextColor(getResources().getColor(R.color.favorite));
            mFavoriteButton.setText(getString(R.string.mark_favorite));
        }
        mFavoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (favoriteExists(mId)) {
                    String WHERE_PARAM = FavoritesProvider.COLUMN_MOVIE_ID + " = " + mId;
                    getContentResolver().delete(FavoritesProvider.CONTENT_URI, WHERE_PARAM, null);
                    ((Button) v).setText(getString(R.string.mark_favorite));
                    ((Button) v).setTextColor(getResources().getColor(R.color.fontLight));
                    Toast.makeText(DetailActivity.this,
                            "Removed from Favorites!", Toast.LENGTH_SHORT).show();
                } else {
                    ContentValues mValues = new ContentValues();
                    mValues.put(FavoritesProvider.COLUMN_MOVIE_ID, mId);
                    mValues.put(FavoritesProvider.COLUMN_TITLE, mTitle);
                    mValues.put(FavoritesProvider.COLUMN_POSTER, mPoster);
                    mValues.put(FavoritesProvider.COLUMN_DESCRIPTION, mDescription);
                    mValues.put(FavoritesProvider.COLUMN_RATING, mRating);
                    mValues.put(FavoritesProvider.COLUMN_RELEASE_DATE, mReleaseDate);
                    getContentResolver().insert(FavoritesProvider.CONTENT_URI, mValues);

                    ((Button) v).setText(getString(R.string.mark_favorite));
                    ((Button) v).setTextColor(getResources().getColor(R.color.favorite));
                    Toast.makeText(DetailActivity.this,
                            "Saved to Favorites", Toast.LENGTH_SHORT).show();
                }
            }
        });

        mShareButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT,
                        "Check out the trailer for" + mTitle + " at " +
                                        TRAILER_BASE_URL + mTrailerKeys[0]);
                startActivity(shareIntent);
            }
        });
    }
    public class FetchTrailersTask extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... strings) {
            try {
                URL trailersRequestUrl = NetworkUtils.buildTrailersUrl(mId);
                return NetworkUtils.getResponseFromHttpUrl(trailersRequestUrl);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            extractTrailerData(result);
            loadTrailerUI();

        }
    }
    public class FetchReviewsTask extends AsyncTask<String, Void, String> {
        @Override
       protected String doInBackground(String... strings) {
            try {
                URL reviewsRequestUrl = NetworkUtils.buildTrailersUrl(mId);
                return NetworkUtils.getResponseFromHttpUrl(reviewsRequestUrl);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }

        @Override
        protected void onPostExecute(String result) {
           extractReviews(result);
            loadReviewUI();
        }
    }

    public void extractTrailerData(String trailersResponse) {
        try {
            JSONObject jsonTrailersObject = new JSONObject(trailersResponse);
            JSONArray trailersResults = jsonTrailersObject.getJSONArray(PARAM_RESULTS);
            mTrailerKeys = new String[trailersResults.length()];
            mTrailerNames = new String[trailersResults.length()];
            for (int i = 0; i < trailersResults.length(); i++)
            {
                mTrailerKeys[i] = trailersResults.getJSONObject(i).optString(PARAM_KEY);
                mTrailerNames[i] = trailersResults.getJSONObject(i).optString(PARAM_NAME);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void loadTrailerUI() {
        if (mTrailerKeys.length == 0) {
            TextView noTrailers = new TextView(this);
            noTrailers.setText(R.string.no_trailers);
            noTrailers.setPadding(0,0,0,50);
            mTrailerList.addView(noTrailers);
        }
        else {
            for (int i = 0; i < mTrailerKeys.length; i++) {
                Button trailerItem = new Button(this);
                trailerItem.setText(mTrailerNames[i]);
                trailerItem.setPadding(0,30, 0,30);
                trailerItem.setTextSize(15);
                final String trailerUrl = TRAILER_BASE_URL + mTrailerKeys[i];
                trailerItem.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // Reference from the Udacity's Lessons on Webpages, Maps, & Sharing
                        Uri youtubeLink = Uri.parse(trailerUrl);
                        Intent youtubeIntent = new Intent(Intent.ACTION_VIEW, youtubeLink);
                        if (youtubeIntent.resolveActivity(getPackageManager()) != null) {
                                startActivity(youtubeIntent);
                        }
                    }
                });
                mTrailerList.addView(trailerItem);
            }
        }
    }
    public void extractReviews(String reviewsResponse) {
        try {
            JSONObject jsonReviewsObject = new JSONObject(reviewsResponse);
            JSONArray reviewsResults = jsonReviewsObject.getJSONArray(PARAM_RESULTS);
            mReviewAuthors = new String[reviewsResults.length()];
            mReviewContent = new String[reviewsResults.length()];
            for (int i = 0; i < reviewsResults.length(); i++)
            {
                mReviewAuthors[i] = reviewsResults.getJSONObject(i).optString(PARAM_AUTHOR);
                mReviewContent[i] = reviewsResults.getJSONObject(i).optString(PARAM_CONTENT);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    public void loadReviewUI() {
        if (mReviewContent.length == 0) {
            findViewById(R.id.author_text).setVisibility(View.GONE);
            findViewById(R.id.context_text).setVisibility(View.GONE);
            findViewById(R.id.next_review_button).setVisibility(View.GONE);

            TextView noReviews = new TextView(this);
            noReviews.setText(R.string.no_reviews);
            noReviews.setPadding(0,0,0,50);
            noReviews.setTextSize(15);
            mReviewList.addView(noReviews);
        } else  {
            if (mReviewContent.length == 1) {
                findViewById(R.id.next_review_button).setVisibility(View.GONE);
            }
            String authorHeader = mReviewAuthors[reviewCounter] + ":";
            ((TextView) findViewById(R.id.author_text)).setText(authorHeader);
            ((TextView) findViewById(R.id.context_text)).setText(mReviewContent[reviewCounter]);
            findViewById(R.id.next_review_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (reviewCounter < mReviewContent.length -1) { reviewCounter++; }
                    else { reviewCounter = 0; }
                    loadReviewUI();
                }
            });
        }
    }

    /* Queries the SQLite database to see if the movie has been favorite. */
    public boolean favoriteExists(String id) {
        Uri uri = FavoritesProvider.CONTENT_URI;
        Cursor cursor = getContentResolver()
                .query(uri, null, null,null);
        if (cursor != null && cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                String movieId = cursor
                        .getString(cursor.getColumnIndex(FavoritesProvider.COLUMN_MOVIE_ID));
                if (id.equals(movieId)) {
                    return true;
                }
                cursor.moveToNext();
            }
            cursor.close();
        }
        return false;
    }
}
