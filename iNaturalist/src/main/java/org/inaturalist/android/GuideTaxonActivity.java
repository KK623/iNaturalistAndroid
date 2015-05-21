package org.inaturalist.android;

import java.util.HashMap;

import org.inaturalist.android.INaturalistService.LoginType;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.flurry.android.FlurryAgent;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.webkit.*;

public class GuideTaxonActivity extends SherlockActivity {
    private static String TAG = "GuideTaxonActivity";
    private static String GUIDE_TAXON_URL = "http://%s/guide_taxa/%d.xml";
    private static String TAXON_URL = "http://%s/taxa/%d";
    private WebView mWebView;
    private INaturalistApp mApp;
    private ActivityHelper mHelper;
	private BetterJSONObject mTaxon;
	private boolean mGuideTaxon;

	@Override
	protected void onStart()
	{
		super.onStart();
		FlurryAgent.onStartSession(this, INaturalistApp.getAppContext().getString(R.string.flurry_api_key));
		FlurryAgent.logEvent(this.getClass().getSimpleName());
	}

	@Override
	protected void onStop()
	{
		super.onStop();		
		FlurryAgent.onEndSession(this);
	}	


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        requestWindowFeature(Window.FEATURE_PROGRESS);

        ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeButtonEnabled(true);
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setIcon(android.R.color.transparent);
        
        mApp = (INaturalistApp) getApplicationContext();
        setContentView(R.layout.web);
        mHelper = new ActivityHelper(this);
        mWebView = (WebView) findViewById(R.id.webview);
        
        Intent intent = getIntent();
        
        if (savedInstanceState == null) {
        	mTaxon = (BetterJSONObject) intent.getSerializableExtra("taxon");
        	mGuideTaxon = intent.getBooleanExtra("guide_taxon", true);
        } else {
        	mTaxon = (BetterJSONObject) savedInstanceState.getSerializable("taxon");
        	mGuideTaxon = savedInstanceState.getBoolean("guide_taxon", true);
        }

        String title = "";
        if (mTaxon.has("display_name") && !mTaxon.getJSONObject().isNull("display_name")) {
        	title = mTaxon.getString("display_name");
        } else {
        	if (mTaxon.has("common_name") && !mTaxon.getJSONObject().isNull("common_name")) {
        		title = mTaxon.getJSONObject("common_name").optString("name", "");
        	} else {
        		title = mTaxon.getJSONObject().optString("name", "");
        	}
        }
        actionBar.setTitle(title);

        mWebView.getSettings().setJavaScriptEnabled(true);

        final Activity activity = this;
        mWebView.setWebViewClient(new WebViewClient() {
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mHelper.loading();
            }
            public void onPageFinished(WebView view, String url) {
                mHelper.stopLoading();
            }
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                mHelper.stopLoading();
                mHelper.alert(String.format(getString(R.string.oh_no), description));
            }
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (!mApp.loggedIn()) {
                    return false;
                }
                mWebView.loadUrl(url, getAuthHeaders());
                return true;
            }
        });

        loadTaxonPage(mTaxon.getInt("id"));
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.guide_taxon_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case android.R.id.home:
            finish();
            return true;
        case R.id.add_taxon:
        	// Add a new observation with the specified taxon
        	Intent intent = new Intent(Intent.ACTION_INSERT, Observation.CONTENT_URI, this, ObservationEditor.class);
        	intent.putExtra(ObservationEditor.SPECIES_GUESS, String.format("%s (%s)", mTaxon.has("display_name") ? mTaxon.getString("display_name") : mTaxon.getJSONObject("common_name").optString("name"), mTaxon.getString("name")));
        	startActivity(intent);

        	return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
    
    public HashMap<String,String> getAuthHeaders() {
        HashMap<String,String> headers = new HashMap<String,String>();
        if (!mApp.loggedIn()) {
            return headers;
        }
        if (mApp.getLoginType() == LoginType.PASSWORD) {
            headers.put("Authorization", "Basic " + mApp.getPrefs().getString("credentials", null));
        } else {
            headers.put("Authorization", "Bearer " + mApp.getPrefs().getString("credentials", null));
        }
        return headers;
    }
    
    public void loadTaxonPage(Integer taxonId) {
    	mWebView.getSettings().setUserAgentString(INaturalistService.USER_AGENT);

    	String url;
    	
        String inatNetwork = mApp.getInaturalistNetworkMember();
        String inatHost = mApp.getStringResourceByName("inat_host_" + inatNetwork);
    	
    	if (mGuideTaxon) {
    		url = String.format(GUIDE_TAXON_URL, inatHost, taxonId);
    	} else {
    		url = String.format(TAXON_URL, inatHost, taxonId);
    	}
    	mWebView.loadUrl(url, getAuthHeaders());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putSerializable("taxon", mTaxon);
        outState.putBoolean("guide_taxon", mGuideTaxon);
        super.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN){
            switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                if (mWebView.canGoBack() == true) {
                    mWebView.goBack();
                } else {
                    finish();
                }
                return true;
            }

        }
        return super.onKeyDown(keyCode, event);
    }

 
}