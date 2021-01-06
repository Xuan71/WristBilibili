package cn.luern0313.wristbilibili.ui;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.drawable.AnimationDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.util.ArrayList;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;
import cn.luern0313.wristbilibili.R;
import cn.luern0313.wristbilibili.api.BangumiApi;
import cn.luern0313.wristbilibili.api.OnlineVideoApi;
import cn.luern0313.wristbilibili.fragment.BangumiDetailFragment;
import cn.luern0313.wristbilibili.fragment.BangumiRecommendFragment;
import cn.luern0313.wristbilibili.fragment.ReplyFragment;
import cn.luern0313.wristbilibili.models.ListBangumiModel;
import cn.luern0313.wristbilibili.models.BangumiModel;
import cn.luern0313.wristbilibili.service.DownloadService;
import cn.luern0313.wristbilibili.util.SharedPreferencesUtil;

public class BangumiActivity extends BaseActivity implements BangumiDetailFragment.BangumiDetailFragmentListener
{
    Context ctx;
    Intent intent;
    LayoutInflater inflater;

    String seasonId;
    BangumiApi bangumiApi;
    BangumiModel bangumiModel;
    OnlineVideoApi onlineVideoApi;
    BangumiReplyActivityListener bangumiReplyActivityListener;
    FragmentPagerAdapter pagerAdapter;
    ArrayList<ListBangumiModel> bangumiRecommendModelArrayList;

    ViewFlipper uiTitle;
    ViewPager uiViewPager;
    ImageView uiLoadingImg;
    LinearLayout uiLoading;
    LinearLayout uiNoWeb;

    boolean isLogin = false;

    AnimationDrawable loadingImgAnim;

    Handler handler = new Handler();
    Runnable runnableUi;
    Runnable runnableNoWeb;
    Runnable runnableNoData;

    private final int RESULT_DETAIL_DOWNLOAD = 101;
    private final int RESULT_DETAIL_SHARE = 102;

    private DownloadService.MyBinder myBinder;
    private final BangumiDownloadServiceConnection connection = new BangumiDownloadServiceConnection();

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bangumi);
        ctx = this;
        intent = getIntent();
        seasonId = intent.getStringExtra("season_id");

        Intent serviceIntent = new Intent(ctx, DownloadService.class);
        bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);

        inflater = getLayoutInflater();

        uiTitle = findViewById(R.id.bgm_title_title);
        uiViewPager = findViewById(R.id.bgm_viewpager);
        uiViewPager.setOffscreenPageLimit(2);
        uiLoadingImg = findViewById(R.id.bgm_loading_img);
        uiLoading = findViewById(R.id.bgm_loading);
        uiNoWeb = findViewById(R.id.bgm_noweb);

        isLogin = SharedPreferencesUtil.contains(SharedPreferencesUtil.cookies);
        bangumiApi = new BangumiApi(seasonId);

        uiLoadingImg.setImageResource(R.drawable.anim_loading);
        loadingImgAnim = (AnimationDrawable) uiLoadingImg.getDrawable();
        loadingImgAnim.start();
        uiLoading.setVisibility(View.VISIBLE);

        runnableUi = () -> {
            ((TextView) uiTitle.findViewWithTag("1")).setText(String.format(getString(R.string.bangumi_title_detail), bangumiModel.getTypeName()));
            ((TextView) uiTitle.findViewWithTag("2")).setText(String.format(getString(R.string.bangumi_title_reply), bangumiModel.getTypeEp()));

            uiLoading.setVisibility(View.GONE);
            uiNoWeb.setVisibility(View.GONE);

            uiViewPager.setAdapter(pagerAdapter);
        };

        runnableNoWeb = () -> {
            try
            {
                uiLoading.setVisibility(View.GONE);
                uiNoWeb.setVisibility(View.VISIBLE);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        };

        runnableNoData = () -> {
            try
            {
                findViewById(R.id.bgm_novideo).setVisibility(View.VISIBLE);
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        };

        pagerAdapter = new FragmentPagerAdapter(getSupportFragmentManager())
        {
            @Override
            public int getCount()
            {
                return 3;
            }

            @NonNull
            @Override
            public Fragment getItem(int position)
            {
                if(position == 0)
                    return BangumiDetailFragment.newInstance(seasonId, bangumiModel);
                else if(position == 1)
                    return ReplyFragment.newInstance(bangumiModel.getUserProgressAid(), "1", null, -1);
                else if(position == 2)
                    return BangumiRecommendFragment.newInstance(bangumiRecommendModelArrayList);
                return null;
            }
        };

        uiViewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener()
        {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels)
            {
            }

            @Override
            public void onPageScrollStateChanged(int state)
            {
            }

            @Override
            public void onPageSelected(int position)
            {
                while(uiTitle.getDisplayedChild() != position)
                {
                    if(uiTitle.getDisplayedChild() < position)
                    {
                        uiTitle.setInAnimation(ctx, R.anim.slide_in_right);
                        uiTitle.setOutAnimation(ctx, R.anim.slide_out_left);
                        uiTitle.showNext();
                    }
                    else
                    {
                        uiTitle.setInAnimation(ctx, android.R.anim.slide_in_left);
                        uiTitle.setOutAnimation(ctx, android.R.anim.slide_out_right);
                        uiTitle.showPrevious();
                    }
                }
            }
        });

        new Thread(() -> {
            try
            {
                bangumiModel = bangumiApi.getBangumiInfo();
                bangumiRecommendModelArrayList = bangumiApi.getBangumiRecommend();
                if(bangumiModel != null)
                {
                    handler.post(runnableUi);
                }
                else
                {
                    handler.post(runnableNoData);
                }
            }
            catch(IOException e)
            {
                e.printStackTrace();
                handler.post(runnableNoWeb);
            }
        }).start();
    }

    @Override
    protected void onActivityResult(final int requestCode, int resultCode, final Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if(resultCode != 0) return;
        if(requestCode == RESULT_DETAIL_DOWNLOAD)
        {
            new Thread(() -> {
                try
                {
                    int position = data.getIntExtra("option_position", 0);
                    String aid = String.valueOf(position < bangumiModel.getEpisodes().size() ? bangumiModel.getEpisodes().get(position).getEpisodeAid() : bangumiModel.getSections().get(position - bangumiModel.getEpisodes().size()).getEpisodeAid());
                    String cid = String.valueOf(position < bangumiModel.getEpisodes().size() ? bangumiModel.getEpisodes().get(position).getEpisodeCid() : bangumiModel.getSections().get(position - bangumiModel.getEpisodes().size()).getEpisodeCid());
                    onlineVideoApi = new OnlineVideoApi(aid, cid);
                    onlineVideoApi.connectionVideoUrl();
                    connection.downloadVideo(data.getStringExtra("option_name") + " - " + bangumiModel.getTitle(), aid, cid);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    Looper.prepare();
                    Toast.makeText(ctx, "网络连接失败，请检查网络", Toast.LENGTH_SHORT).show();
                    Looper.loop();
                }
            }).start();
        }
        else if(requestCode == RESULT_DETAIL_SHARE)
        {
            new Thread(() -> {
                try
                {
                    String result = bangumiApi.shareBangumi(data.getStringExtra("text"));
                    if(result.equals(""))
                    {
                        Looper.prepare();
                        Toast.makeText(ctx, "发送成功！", Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }
                    else
                    {
                        Looper.prepare();
                        Toast.makeText(ctx, result, Toast.LENGTH_SHORT).show();
                        Looper.loop();
                    }
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    Looper.prepare();
                    Toast.makeText(ctx, "分享视频失败。。请检查网络？", Toast.LENGTH_SHORT).show();
                    Looper.loop();
                }
            }).start();
        }
    }

    @Override
    public void onBangumiDetailFragmentViewClick(int viewId)
    {
        if(viewId == R.id.bgm_detail_bt_follow_lay)
        {
            new Thread(() -> {
                try
                {
                    String result = bangumiApi.followBangumi(!bangumiModel.isUserIsFollow());
                    Looper.prepare();
                    Toast.makeText(ctx, result, Toast.LENGTH_SHORT).show();
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                    Looper.prepare();
                    Toast.makeText(ctx, getString(R.string.main_error_web), Toast.LENGTH_SHORT).show();
                }
                finally
                {
                    EventBus.getDefault().post(bangumiModel);
                    Looper.loop();
                }
            }).start();
        }
        else if(viewId == R.id.bgm_detail_bt_cover_lay)
        {
            Intent intent = new Intent(ctx, ImgActivity.class);
            intent.putExtra("imgUrl", new String[]{bangumiModel.getCover()});
            startActivity(intent);
        }
        else if(viewId == R.id.bgm_detail_bt_download_lay)
        {
            if(bangumiModel.isRightDownload())
            {
                String[] videoPartNames = new String[bangumiModel.getEpisodes().size() + bangumiModel.getSections().size()];
                for (int i = 0; i < bangumiModel.getEpisodes().size(); i++)
                    videoPartNames[i] = BangumiModel.getTitle(1, bangumiModel, bangumiModel.getEpisodes().get(i), " ");
                for (int i = bangumiModel.getEpisodes().size(); i < videoPartNames.length; i++)
                    videoPartNames[i] = BangumiModel.getTitle(2, bangumiModel, bangumiModel.getEpisodes().get(i), " ");
                Intent intent = new Intent(ctx, SelectPartActivity.class);
                intent.putExtra("title", getString(R.string.bangumi_download_title));
                intent.putExtra("tip", getString(R.string.bangumi_download_tip));
                intent.putExtra("options_name", videoPartNames);
                startActivityForResult(intent, RESULT_DETAIL_DOWNLOAD);
            }
            else
                Toast.makeText(ctx, String.format(getString(R.string.bangumi_download_notallow), bangumiModel.getTypeName()), Toast.LENGTH_SHORT).show();
        }
        else if(viewId == R.id. bgm_detail_bt_share_lay)
        {
            Intent intent = new Intent(ctx, SendDynamicActivity.class);
            intent.putExtra("is_share", true);
            intent.putExtra("share_up", bangumiModel.getTitle());
            intent.putExtra("share_title", BangumiModel.getTitle(bangumiModel.getUserProgressMode(), bangumiModel, bangumiModel.getEpisodes().get(bangumiModel.getUserProgressPosition()), " "));
            intent.putExtra("share_img", bangumiModel.getCoverSmall());
            startActivityForResult(intent, RESULT_DETAIL_SHARE);
        }
    }

    @Override
    public void onBangumiDetailFragmentReplyUpdate(String epId, int mode, int position, String aid)
    {
        bangumiModel.setUserProgressEpId(epId);
        bangumiModel.setUserProgressMode(mode);
        bangumiModel.setUserProgressPosition(position);
        bangumiModel.setUserProgressAid(aid);
        bangumiReplyActivityListener.onBangumiReplyUpdate(bangumiModel.getUserProgressAid(), "1");
    }

    class BangumiDownloadServiceConnection implements ServiceConnection
    {
        @Override
        public void onServiceDisconnected(ComponentName name)
        {
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service)
        {
            myBinder = (DownloadService.MyBinder) service;
        }

        void downloadVideo(String title, String aid, String cid)
        {
            String result = myBinder.startDownload(aid, cid, title, bangumiModel.getCoverSmall(), onlineVideoApi.getVideoUrl(), onlineVideoApi.getDanmakuUrl());
            Looper.prepare();
            if(result.equals("")) Toast.makeText(ctx, "已添加至下载列表", Toast.LENGTH_SHORT).show();
            else Toast.makeText(ctx, result, Toast.LENGTH_SHORT).show();
            Looper.loop();
        }
    }

    public void setBangumiReplyActivityListener(BangumiReplyActivityListener bangumiReplyActivityListener)
    {
        this.bangumiReplyActivityListener = bangumiReplyActivityListener;
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        try
        {
            unbindService(connection);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    public interface BangumiReplyActivityListener
    {
        void onBangumiReplyUpdate(String oid, String reply);
    }
}
