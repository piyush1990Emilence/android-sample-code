
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

//import com.vincent.videocompressor.VideoCompress;

public class MediaPreviewActivity extends BaseActivity implements View.OnClickListener, passMediaData {

    public static com.utils.trimmer.interfaces.passMediaData passMediaData;
    public static boolean isCanceled = false;
    private MediaPreview mediaPreview;
    private BottomMediaPreview bottomMediaPreview;
    private RecyclerView recyBottom;
    private ViewPager viewPagerPreview;
    private List<PojoGallery> selectedMedia;
    private RelativeLayout rlMoreImages;
    private ImageButton imgBtnSendMedia, imgBtnBack, imgBtnAddImage, imgBtnDelete;
    private MediaPreviewAdapter mediaPreviewAdapter;
    private ProgressBar progressBar;
    private boolean isGoingNextActivity = false;
    private String check = "";
    private PojoConversation conversation = new PojoConversation();
    private List<String> selectedConversations = new ArrayList<>();
    private List<RealmGetAllConversation> conversationList = new ArrayList<>();
    private List<RealmGetAllConversation> archiveConversationList = new ArrayList<>();
    private List<PojoFollower> followerList = new ArrayList<>();
    private RealmDatabaseHandler realmDatabaseHandler;
    private PojoLoginData userData;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_media_preview);

        realmDatabaseHandler = RealmDatabaseHandler.getRealmDatabaseHandler();
        userData = realmDatabaseHandler.getUserInfo();

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            selectedMedia = new Gson().fromJson(bundle.getString(Constant.KEY_DATA),
                    new TypeToken<List<PojoGallery>>() {
                    }.getType());///storage/emulated/0/DCIM/BaBBleU_VID_1568987697538.mp4
            check = bundle.getString(Constant.KEY_CHECK);
            if (check.equals(Constant.SEND_MEDIA_TO_MULTIPLE_USER)) {
                selectedConversations = new Gson().fromJson(bundle.getString(Constant.KEY_SELECTED_LIST),
                        new TypeToken<List<String>>() {
                        }.getType());
                conversationList.addAll(realmDatabaseHandler.getUnarchiveConversation());
                archiveConversationList.addAll(realmDatabaseHandler.getArchiveConversation());
                followerList = new Gson().fromJson(bundle.getString(Constant.KEY_FOLLOWER_LIST),
                        new TypeToken<List<PojoFollower>>() {
                        }.getType());
            } else {
                conversation = new Gson().fromJson(bundle.getString(Constant.KEY_CONVERSATION), new TypeToken<PojoConversation>() {
                }.getType());
            }
        }

        passMediaData = this;
        viewPagerPreview = findViewById(R.id.viewPagerPreview);
        recyBottom = findViewById(R.id.recyBottom);
        rlMoreImages = findViewById(R.id.rlMoreImages);
        imgBtnSendMedia = findViewById(R.id.imgBtnSendMedia);
        imgBtnBack = findViewById(R.id.imgBtnBack);
        imgBtnAddImage = findViewById(R.id.imgBtnAddImage);
        imgBtnDelete = findViewById(R.id.imgBtnDelete);
        progressBar = findViewById(R.id.progressBar);
        imgBtnSendMedia.setOnClickListener(this);
        imgBtnBack.setOnClickListener(this);
        imgBtnAddImage.setOnClickListener(this);
        imgBtnDelete.setOnClickListener(this);
        isCanceled = false;

        mediaPreviewAdapter = new MediaPreviewAdapter(this, getSupportFragmentManager(), selectedMedia, conversation);
        viewPagerPreview.setAdapter(mediaPreviewAdapter);

        recyBottom.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        mediaPreview = new MediaPreview(this, selectedMedia, getSupportFragmentManager(), conversation);
        bottomMediaPreview = new BottomMediaPreview(this, selectedMedia, 0);
        recyBottom.setAdapter(bottomMediaPreview);
        viewPagerPreview.setSaveFromParentEnabled(false);

        if (selectedMedia.size() != 1) {
            imgBtnDelete.setVisibility(View.VISIBLE);
            rlMoreImages.setVisibility(View.VISIBLE);
        } else {
            rlMoreImages.setVisibility(View.GONE);
            imgBtnDelete.setVisibility(View.GONE);
        }

        viewPagerPreview.setCurrentItem(selectedMedia.size() - 1);
        bottomMediaPreview.notifyList(selectedMedia.size() - 1);

        recyBottom.addOnItemTouchListener(new RecyclerItemClickListener(this, recyBottom, new RecyclerItemClickListener.OnItemClickListener() {
            @Override
            public void onItemClick(View view, int position) {
                viewPagerPreview.setCurrentItem(position);
                bottomMediaPreview.notifyList(position);
            }

            @Override
            public void onItemLongClick(View view, int position) {
            }
        }));

        viewPagerPreview.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                bottomMediaPreview.notifyList(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.imgBtnSendMedia:

                isGoingNextActivity = true;
                DashboardActivity.isGoingToNextScreen = false;
                MultiplePhotosActivity.isGoingNextActivityMultiple = false;
                progressBar.setVisibility(View.VISIBLE);
                imgBtnSendMedia.setImageResource(R.drawable.ic_send_unselected);
                imgBtnSendMedia.setEnabled(false);
                if (check.equals(Constant.CUSTOM_GIPHY_SEND)) {
                    EventBus.getDefault().post(new MessageEventSendCustomGIf(conversation, selectedMedia.get(0).getImage(), 0));
                    finish();
                } else {
                    mediaPreviewAdapter.getTrimmedVideos(check, selectedConversations, followerList, conversationList, archiveConversationList, conversation);
                }
                break;
            case R.id.imgBtnBack:
            case R.id.imgBtnAddImage:
                isGoingNextActivity = true;
                MultiplePhotosActivity.isGoingNextActivityMultiple = false;
                finish();
                break;
            case R.id.imgBtnDelete:
                int position = viewPagerPreview.getCurrentItem();
                selectedMedia.remove(position);
                mediaPreviewAdapter.remove(position, selectedMedia);

                if (position == selectedMedia.size() && position != 0) {

                    bottomMediaPreview.notifyList(position - 1);
                } else if (0 == position) {
                    bottomMediaPreview.notifyList(position);
                }

                EventBus.getDefault().post(new MediaPreviewItemDeletedEvent(position));

                if (0 == selectedMedia.size()) {

                    finish();
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        viewPagerPreview.setAdapter(null);
        isCanceled = true;
        BackgroundExecutor.cancelAll("abc", true);
    }

    @Override
    public void onPassMediaData(int adapterPosition, int startThumbPosition, int endThumbPosition, int startTime) {
        selectedMedia.get(adapterPosition).setmStartTime(startTime);
        selectedMedia.get(adapterPosition).setmThumbStartPosition(startThumbPosition);
        selectedMedia.get(adapterPosition).setmThumbEndPosition(endThumbPosition);
        selectedMedia.get(adapterPosition).setActive(true);
    }

    @Override
    protected void onResume() {
        super.onResume();

        RealmDatabaseHandler realmDatabaseHandler = RealmDatabaseHandler.getRealmDatabaseHandler();
        PojoAppAuthentication authentication = realmDatabaseHandler.getAppAuthentication();
        if (MyApplication.isAppBackground && authentication != null && (authentication.getIsWholeAppLocked() == 1 && (authentication.getIsPasscodeEnabled() == 1 || authentication.getIsBiometricsEnabled() == 1))) {
            GlobalFunction.wholeAppTimeCheck(authentication, this, getSupportFragmentManager(), R.id.rlBase);
        }
        MyApplication.isAppBackground = false;
    }

    @Override
    protected void onPause() {
        super.onPause();

        if (!isGoingNextActivity) {
            MyApplication.isAppBackground = true;
        }
    }


    @Override
    public void onBackPressed() {
        PassCodeFragment passCodeFragment1 = (PassCodeFragment) getSupportFragmentManager().findFragmentByTag(getString(R.string.pass_code));
        if (passCodeFragment1 == null) {
            super.onBackPressed();
            isGoingNextActivity = true;
            MultiplePhotosActivity.isGoingNextActivityMultiple = false;
            finish();
        }
    }


    public void mediaUpload(List<PojoSelectedMediaFile> mediaFiles, PojoConversation conversation) {

        mediaUploaded(mediaFiles,conversation);
    }
}
