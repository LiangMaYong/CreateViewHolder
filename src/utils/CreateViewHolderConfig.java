package utils;

/**
 * Created by Administrator on 2016/10/29.
 */
public final class CreateViewHolderConfig {

    public static final String DIALIG_TITLE = "CreateViewHodler";
    public static final String PREFIX = "m_";
    public static final String VIEWHOLDER_CLASS_NAME = "ViewHolder";

    ///////////////////////////////////////////////////////////////////////////////////////
    private static final String BindView = "@CreateViewHolder";
    private static final String BindOnClick = "@BindOnClick";
    private static final String BindOnLongClick = "@BindOnLongClick";
    public static final String ButterknifeBindView = "@CreateViewHolder";
    public static final String ButterknifeBindOnClick = "@OnClick";
    public static final String ButterknifeBindOnLongClick = "@OnLongClick";

    public static String getBindView(boolean isButter) {
        if (isButter) {
            return ButterknifeBindView;
        } else {
            return BindView;
        }
    }

    public static String getBindOnClick(boolean isButter) {
        if (isButter) {
            return ButterknifeBindOnClick;
        } else {
            return BindOnClick;
        }
    }

    public static String getBindOnLongClick(boolean isButter) {
        if (isButter) {
            return ButterknifeBindOnLongClick;
        } else {
            return BindOnLongClick;
        }
    }
}
