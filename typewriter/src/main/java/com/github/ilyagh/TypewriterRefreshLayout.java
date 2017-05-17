package com.github.ilyagh;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.ViewCompat;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.Transformation;
import android.widget.AbsListView;
import android.widget.ImageView;


/**
 * The {@link TypewriterRefreshLayout} should be used whenever the user can refresh the
 * contents of a view via a vertical swipe gesture. The activity that instantiates this view should
 * add an {@link TypewriterRefreshLayout.OnRefreshListener} to be notified whenever the swipe
 * to refresh gesture is completed.
 * The {@link TypewriterRefreshLayout} will notify the listener each and every time the gesture
 * is completed again; the listener is responsible for correctly determining when to actually
 * initiate a refresh of its content. If the listener determines there should not be a refresh, it
 * must call {@link TypewriterRefreshLayout#setRefreshing(boolean)} with {@link Boolean#FALSE}
 * param to cancel any visual indication of a refresh.
 * If an activity wishes to show just the progress animation, it should call
 * {@link TypewriterRefreshLayout#setRefreshing(boolean)} with {@link Boolean#TRUE} param.
 * To disable the gesture and progress animation, call
 * {@link TypewriterRefreshLayout#setRefreshing(boolean)} with {@link Boolean#FALSE} param on the view.
 * <p>
 * <p><b>Note!</b> To call the {@link TypewriterRefreshLayout#setRefreshing(boolean)} method
 * you must wrap it in {@link TypewriterRefreshLayout#post(Runnable)}</p>
 * <p>
 * This layout should be made the parent of the view that will be refreshed as a result of the
 * gesture and can only support one direct child. This view will also be made the target of the
 * gesture and will be forced to match both the width and the height supplied in this layout.
 * The SwipeRefreshLayout does not provide accessibility events; instead, a menu item must be
 * provided to allow refresh of the content wherever this gesture is used.
 * <p>
 * Supported child views: RecyclerView, ListView, ScrollView, NestedScrollView etc.
 * </p>
 */
public class TypewriterRefreshLayout extends ViewGroup {
    private static final String EXTRA_SUPER_STATE = "EXTRA_SUPER_STATE";
    private static final String EXTRA_IS_REFRESHING = "EXTRA_IS_REFRESHING";
    private static final int INVALID_POINTER_ID = -1;

    private static final float DRAG_RATE = .85f;
    private static final float DECELERATE_INTERPOLATION_FACTOR = 2.0f;

    private static final int MAX_OFFSET_ANIMATION_DURATION = 700;
    private static final int MAX_DRAG_DISTANCE = 140;

    @Nullable
    private OnChildScrollUpCallback onChildScrollUpCallback;

    private int totalDragDistance;
    private int touchSlop;

    private int targetPaddingTop;
    private int targetPaddingBottom;
    private int targetPaddingRight;
    private int targetPaddingLeft;

    private int from;
    private int currentOffsetTop;
    private int activePointerId;
    private float fromDragPercent;
    private float currentDragPercent;
    private float initialMotionY;

    private boolean isRefreshing;
    private boolean isBeingDragged;

    private View target;
    private ImageView refreshView;
    private TypewriterRefreshDrawable refreshDrawable;

    private final Interpolator decelerateInterpolator = new DecelerateInterpolator(DECELERATE_INTERPOLATION_FACTOR);
    private final Animation animateToStartPosition = new Animation() {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            moveToStart(interpolatedTime);
        }
    };
    private final Animation animateToCorrectPosition = new Animation() {
        @Override
        protected void applyTransformation(float interpolatedTime, Transformation t) {
            int targetTop;
            int endTarget = totalDragDistance;
            targetTop = (from + (int) ((endTarget - from) * interpolatedTime));
            int offset = targetTop - target.getTop();

            currentDragPercent = fromDragPercent - (fromDragPercent - 1.0f) * interpolatedTime;
            refreshDrawable.setPercent(currentDragPercent, false);

            if (refreshDrawable.isSkipAnimation()) {
                refreshDrawable.setOffsetTopAndBottom(0);
                currentOffsetTop = target.getTop();
            } else {
                setTargetOffsetTop(offset, false);
            }
        }
    };
    private final Animation.AnimationListener mToStartListener = new SimpleAnimationListener() {
        @Override
        public void onAnimationEnd(Animation animation) {
            refreshDrawable.stop();
            currentOffsetTop = target.getTop();
        }
    };
    private OnRefreshListener mOnRefreshListener;

    /**
     * Simple constructor to use when creating a {@link TypewriterRefreshLayout} from code.
     *
     * @param context
     */
    public TypewriterRefreshLayout(Context context) {
        this(context, null);
    }

    /**
     * Constructor that is called when inflating {@link TypewriterRefreshLayout} from XML.
     *
     * @param context
     * @param attrs
     */
    public TypewriterRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        if (getChildCount() > 1) {
            throw new RuntimeException("You can attach only one child to the TypewriterRefreshLayout!");
        }

        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        refreshView = new ImageView(context);
        totalDragDistance = Utils.convertDpToPixel(context, MAX_DRAG_DISTANCE);
        refreshDrawable = new TypewriterRefreshDrawable(this);

        refreshView.setImageDrawable(refreshDrawable);

        addView(refreshView);
        setWillNotDraw(false);
        ViewCompat.setChildrenDrawingOrderEnabled(this, true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        View targetView = getTargetView();
        if (targetView != null) {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredWidth() - getPaddingLeft() - getPaddingRight(), MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(getMeasuredHeight() - getPaddingBottom() - getPaddingTop(), MeasureSpec.EXACTLY);

            targetView.measure(widthMeasureSpec, heightMeasureSpec);
            refreshView.measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        View targetView = getTargetView();
        if (targetView != null) {
            int height = getMeasuredHeight();
            int width = getMeasuredWidth();
            int left = getPaddingLeft();
            int top = getPaddingTop();
            int right = getPaddingRight();
            int bottom = getPaddingBottom();

            targetView.layout(left, top + currentOffsetTop, left + width - right, top + height - bottom + currentOffsetTop);
            refreshView.layout(left, top, left + width - right, top + height - bottom);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent motionEvent) {
        if (!isEnabled() || canChildScrollUp() || isRefreshing) {
            return false;
        }

        switch (MotionEventCompat.getActionMasked(motionEvent)) {
            case MotionEvent.ACTION_DOWN:
                setTargetOffsetTop(0, true);
                activePointerId = motionEvent.getPointerId(0);
                isBeingDragged = false;
                final float initialMotionY = getMotionEventY(motionEvent, activePointerId);
                if (initialMotionY == -1f) {
                    return false;
                }
                this.initialMotionY = initialMotionY;
                break;
            case MotionEvent.ACTION_MOVE:
                if (activePointerId == INVALID_POINTER_ID) {
                    return false;
                }

                final float y = getMotionEventY(motionEvent, activePointerId);
                if (y == -1f) {
                    return false;
                }
                final float yDiff = y - this.initialMotionY;
                if (yDiff > touchSlop && !isBeingDragged) {
                    isBeingDragged = true;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isBeingDragged = false;
                activePointerId = INVALID_POINTER_ID;
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(motionEvent);
                break;
        }

        return isBeingDragged;
    }

    @Override
    protected Parcelable onSaveInstanceState() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_SUPER_STATE, super.onSaveInstanceState());
        bundle.putBoolean(EXTRA_IS_REFRESHING, isRefreshing);
        return bundle;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state instanceof Bundle) {
            final Bundle bundle = ((Bundle) state);
            super.onRestoreInstanceState(bundle.getParcelable(EXTRA_SUPER_STATE));
            if (bundle.getBoolean(EXTRA_IS_REFRESHING)) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        refreshDrawable.setSkipAnimation(true);
                        setRefreshing(true, false);
                    }
                });
            }
        }
    }

    private float getMotionEventY(MotionEvent motionEvent, int activePointerId) {
        final int index = motionEvent.findPointerIndex(activePointerId);
        if (index < 0) {
            return -1f;
        }
        return motionEvent.getY(index);
    }

    private void onSecondaryPointerUp(MotionEvent motionEvent) {
        final int pointerIndex = MotionEventCompat.getActionIndex(motionEvent);
        final int pointerId = motionEvent.getPointerId(pointerIndex);
        if (pointerId == activePointerId) {
            activePointerId = motionEvent.getPointerId(pointerIndex == 0 ? 1 : 0);
        }
    }

    @Override
    public boolean onTouchEvent(@NonNull MotionEvent motionEvent) {
        if (!isBeingDragged) {
            return super.onTouchEvent(motionEvent);
        }

        switch (MotionEventCompat.getActionMasked(motionEvent)) {
            case MotionEvent.ACTION_MOVE: {
                final int pointerIndex = motionEvent.findPointerIndex(activePointerId);
                if (pointerIndex != 0) {
                    return false;
                }
                final float x = motionEvent.getX(pointerIndex);
                final float y = motionEvent.getY(pointerIndex);
                final float yDiff = y - initialMotionY;
                final float scrollTop = yDiff * DRAG_RATE;
                currentDragPercent = scrollTop / totalDragDistance;
                if (currentDragPercent < 0) {
                    return false;
                }
                float boundedDragPercent = Math.min(1f, Math.abs(currentDragPercent));
                float slingshotDist = totalDragDistance;
                int targetY = (int) ((slingshotDist * boundedDragPercent));

                refreshDrawable.setPercent(currentDragPercent, true);
                setTargetOffsetTop(targetY - currentOffsetTop, true);
                break;
            }
            case MotionEventCompat.ACTION_POINTER_DOWN:
                activePointerId = motionEvent.getPointerId(MotionEventCompat.getActionIndex(motionEvent));
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onSecondaryPointerUp(motionEvent);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (activePointerId == INVALID_POINTER_ID) {
                    return false;
                }
                final float y = motionEvent.getY(motionEvent.findPointerIndex(activePointerId));
                final float overScrollTop = (y - initialMotionY) * DRAG_RATE;
                isBeingDragged = false;
                if (overScrollTop > totalDragDistance) {
                    setRefreshing(true, true);
                } else {
                    isRefreshing = false;
                    animateOffsetToStartPosition();
                }
                activePointerId = INVALID_POINTER_ID;
                return false;
            }
        }

        return true;
    }

    /**
     * @return Whether it is possible for the child view of this layout to
     * scroll up. Override this if the child view is a custom view.
     */
    public boolean canChildScrollUp() {
        if (onChildScrollUpCallback != null) {
            return onChildScrollUpCallback.canChildScrollUp(this, target);
        }
        if (android.os.Build.VERSION.SDK_INT < 14) {
            if (target instanceof AbsListView) {
                final AbsListView absListView = (AbsListView) target;
                return absListView.getChildCount() > 0
                        && (absListView.getFirstVisiblePosition() > 0 ||
                        absListView.getChildAt(0).getTop() < absListView.getPaddingTop());
            } else {
                return ViewCompat.canScrollVertically(target, -1) || target.getScrollY() > 0;
            }
        } else {
            return ViewCompat.canScrollVertically(target, -1);
        }
    }

    /**
     * @return max drag distance in pixels
     */
    public int getTotalDragDistance() {
        return totalDragDistance;
    }

    /**
     * @return Whether the {@link TypewriterRefreshLayout} is actively showing refresh
     * progress.
     */
    public boolean isRefreshing() {
        return isRefreshing;
    }

    /**
     * Notify the widget that refresh state has changed. Do not call this when
     * refresh is triggered by a swipe gesture.
     * <b>Note!</b> You must wrap calling this method in {@link TypewriterRefreshLayout#post(Runnable)}
     *
     * @param refreshing Whether or not the view should show refresh progress.
     */
    public void setRefreshing(boolean refreshing) {
        setRefreshing(refreshing, false);
    }

    private void setRefreshing(boolean refreshing, final boolean notify) {
        if (isRefreshing != refreshing) {

            isRefreshing = refreshing;
            if (isRefreshing) {
                refreshDrawable.setPercent(1f, true);
                from = currentOffsetTop;
                fromDragPercent = currentDragPercent;

                animateToCorrectPosition.reset();
                animateToCorrectPosition.setDuration(MAX_OFFSET_ANIMATION_DURATION);
                animateToCorrectPosition.setInterpolator(decelerateInterpolator);

                refreshView.clearAnimation();
                refreshView.startAnimation(animateToCorrectPosition);

                if (isRefreshing) {
                    refreshDrawable.start();
                    if (notify && null != mOnRefreshListener) {
                        mOnRefreshListener.onRefresh();
                    }
                } else {
                    refreshDrawable.stop();
                    animateOffsetToStartPosition();
                }

                currentOffsetTop = target.getTop();
                target.setPadding(targetPaddingLeft, targetPaddingTop, targetPaddingRight, targetPaddingBottom);
            } else {
                animateOffsetToStartPosition();
            }
        }
    }

    @Nullable
    View getTargetView() {
        if (target == null) {
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child != refreshView) {
                    target = child;
                    targetPaddingBottom = target.getPaddingBottom();
                    targetPaddingLeft = target.getPaddingLeft();
                    targetPaddingRight = target.getPaddingRight();
                    targetPaddingTop = target.getPaddingTop();
                }
            }
        }
        return target;
    }

    private void animateOffsetToStartPosition() {
        fromDragPercent = currentDragPercent;
        from = currentOffsetTop;
        long animationDuration = Math.abs((long) (MAX_OFFSET_ANIMATION_DURATION * fromDragPercent));

        animateToStartPosition.reset();
        animateToStartPosition.setDuration(animationDuration);
        animateToStartPosition.setInterpolator(decelerateInterpolator);
        animateToStartPosition.setAnimationListener(mToStartListener);
        refreshView.clearAnimation();
        refreshView.startAnimation(animateToStartPosition);
    }

    private void moveToStart(float interpolatedTime) {
        int targetTop = from - (int) (from * interpolatedTime);

        currentDragPercent = fromDragPercent * (1.0f - interpolatedTime);
        refreshDrawable.setPercent(currentDragPercent, true);
        target.setPadding(targetPaddingLeft, targetPaddingTop, targetPaddingRight, targetPaddingBottom + targetTop);

        target.offsetTopAndBottom(targetTop - target.getTop());
        refreshDrawable.setOffsetTopAndBottom((int) (-getTotalDragDistance() * interpolatedTime));
        currentOffsetTop = target.getTop();
    }

    private void setTargetOffsetTop(int offset, boolean requiresUpdate) {
        target.offsetTopAndBottom(offset);
        refreshDrawable.offsetTopAndBottom(offset);
        currentOffsetTop = target.getTop();
        if (requiresUpdate && Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
            invalidate();
        }
    }

    /**
     * Set the listener to be notified when a refresh is triggered via the swipe gesture.
     */
    public void setOnRefreshListener(OnRefreshListener onRefreshListener) {
        mOnRefreshListener = onRefreshListener;
    }

    /**
     * Set a callback to override {@link TypewriterRefreshLayout#canChildScrollUp()} method. Non-null
     * callback will return the value provided by the callback and ignore all internal logic.
     *
     * @param callback Callback that should be called when canChildScrollUp() is called.
     */
    public void setOnChildScrollUpCallback(@Nullable TypewriterRefreshLayout.OnChildScrollUpCallback callback) {
        onChildScrollUpCallback = callback;
    }

    /**
     * Classes that wish to be notified when the swipe gesture correctly
     * triggers a refresh should implement this interface.
     */
    public interface OnRefreshListener {
        /**
         * Called when a swipe gesture triggers a refresh.
         */
        void onRefresh();
    }

    /**
     * Classes that wish to override {@link TypewriterRefreshLayout#canChildScrollUp()} method
     * behavior should implement this interface.
     */
    public interface OnChildScrollUpCallback {
        /**
         * Callback that will be called when {@link TypewriterRefreshLayout#canChildScrollUp()} method
         * is called to allow the implementer to override its behavior.
         *
         * @param parent {@link TypewriterRefreshLayout} that this callback is overriding.
         * @param child  The child view of {@link TypewriterRefreshLayout}.
         * @return Whether it is possible for the child view of parent layout to scroll up.
         */
        boolean canChildScrollUp(@NonNull TypewriterRefreshLayout parent, @Nullable View child);
    }
}
