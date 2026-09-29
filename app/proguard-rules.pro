# Proguard rules for glauncher

# Keep custom view classes referenced in XML layouts
-keep class br.com.nqber.glauncher.SquareFrameLayout {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep class br.com.nqber.glauncher.CircularRangeView {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep AppInfo properties
-keep class br.com.nqber.glauncher.AppInfo { *; }
