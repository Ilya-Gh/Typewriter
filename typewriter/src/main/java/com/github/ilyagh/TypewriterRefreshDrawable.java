package com.github.ilyagh;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ScaleDrawable;
import android.support.annotation.NonNull;
import android.support.v4.content.ContextCompat;
import com.github.ilyagh.typewriter.R;
import java.util.ArrayList;
import java.util.List;

import static android.view.Gravity.TOP;

class TypewriterRefreshDrawable extends BaseRefreshDrawable {

    private TypewriterRefreshLayout parent;

    private boolean hasAnimationStarted = false;
    private boolean hasPageAnimationStarted = false;
    private boolean skipAnimation = false;

    private float percent;
    private int currentStep = FIRST_STEP;
    private int pageRotateCycle;

    private int screenWidth;
    private int top;

    private static final int CARRIAGE_ANIMATION_DURATION_MS = 2000;
    private static final int CARRIAGE_ANIMATION_RETURN_DURATION_MS = 500;

    private static final int FIRST_STEP = 1;
    private static final int LAST_STEP = 17;

    private static final int EMPTY_KEY = -1;
    private static final int SPACE_KEY = -2;

    private static final int TOTAL_NUMBER_OF_PHYSICAL_KEYS = 23;
    private static final int TOP_LINE_KEYS_NUMBER = 9;
    private static final int MIDDLE_LINE_KEYS_NUMBER = 10;
    private static final int BOTTOM_LINE_KEYS_NUMBER = 9;
    private static final int SPACE_START_POSITION = 2;
    private static final int SPACE_END_POSITION = 6;

    private static final float BACKGROUND_RATIO = 0.5f;
    private static final int PAGE_CYCLE = 3;
    private int backgroundHeight;

    private final ValueAnimator carriageAnimator = new ValueAnimator();
    private final ValueAnimator carriageReturnAnimator = new ValueAnimator();
    private final ValueAnimator pageAnimator = new ValueAnimator();

    private List<Drawable> parts;
    private Drawable button;
    private Drawable buttonPressed;
    private Drawable space;
    private Drawable spacePressed;
    private Drawable keyboard;
    private Drawable letter;
    private ScaleDrawable page;
    private ScaleDrawable pageBack;
    private Drawable typewriter;
    private Canvas canvas;

    private int carriageOffset;
    private int offset;
    private int carriageX;
    private float pagePercent;
    private int pressedKey = EMPTY_KEY;
    private int pageOffset;
    private int numberOfLettersOnString[];
    private int lastType;
    private List<List<Integer>> letterOffset = new ArrayList<>();

    TypewriterRefreshDrawable(final TypewriterRefreshLayout layout) {
        super(layout);
        parent = layout;

        layout.post(new Runnable() {
            @Override
            public void run() {
                init();
            }
        });
    }

    @Override
    protected void init() {
        int viewWidth = parent.getWidth();
        if (viewWidth <= 0 || viewWidth == screenWidth) {
            return;
        }

        setupDrawables();
        setupAnimations();

        screenWidth = viewWidth;
        backgroundHeight = (int) (BACKGROUND_RATIO * screenWidth);

        top = -parent.getTotalDragDistance();
        for (int i = 0; i < PAGE_CYCLE; i++) {
            letterOffset.add(new ArrayList<Integer>());
        }
        numberOfLettersOnString = new int[3];
    }

    private void setupDrawables() {
        parts = new ArrayList<>();
        parts.add(ContextCompat.getDrawable(getContext(), R.drawable.carriage_part1));
        parts.add(ContextCompat.getDrawable(getContext(), R.drawable.carriage_part2));
        parts.add(ContextCompat.getDrawable(getContext(), R.drawable.carriage_part3));

        carriageOffset = (int) getContext().getResources().getDimension(R.dimen.carriage_offset);
        pageOffset = (int) getContext().getResources().getDimension(R.dimen.page_offset);
        offset = (int) getContext().getResources().getDimension(R.dimen.offset);

        button = ContextCompat.getDrawable(getContext(), R.drawable.button);
        buttonPressed = ContextCompat.getDrawable(getContext(), R.drawable.button_pressed);

        page = new ScaleDrawable(ContextCompat.getDrawable(getContext(), R.drawable.page),
                TOP, -1, 1);
        page.setLevel(10000);
        pageBack =
                new ScaleDrawable(ContextCompat.getDrawable(getContext(), R.drawable.page_revers),
                        TOP, -1, 1);
        pageBack.setLevel(0);

        keyboard = ContextCompat.getDrawable(getContext(), R.drawable.keyboard_bg);
        typewriter = ContextCompat.getDrawable(getContext(), R.drawable.machine);
        space = ContextCompat.getDrawable(getContext(), R.drawable.space);
        spacePressed = ContextCompat.getDrawable(getContext(), R.drawable.space_pressed);
        letter = ContextCompat.getDrawable(getContext(), R.drawable.letter);
    }

    @Override
    protected void setupAnimations() {
        carriageAnimator.cancel();
        carriageAnimator.setDuration(CARRIAGE_ANIMATION_DURATION_MS);
        carriageAnimator.setIntValues(FIRST_STEP, LAST_STEP);
        carriageAnimator.setRepeatCount(ValueAnimator.INFINITE);
        carriageAnimator.setRepeatMode(ValueAnimator.RESTART);
        carriageAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int value = (int) valueAnimator.getAnimatedValue();
                currentStep = !skipAnimation ? value : 1;
                invalidateSelf();
                if (skipAnimation) {
                    valueAnimator.cancel();
                }
            }
        });

        carriageAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationRepeat(Animator animation) {
                carriageAnimator.pause();
                carriageReturnAnimator.start();
                if (pageRotateCycle < PAGE_CYCLE - 1) {
                    pageRotateCycle++;
                    currentStep = FIRST_STEP;
                    pressedKey = EMPTY_KEY;
                } else {
                    resetAnimation();
                }
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                resetAnimation();
            }
        });

        carriageReturnAnimator.cancel();
        carriageReturnAnimator.setFloatValues(0.5f, 0.01f);
        carriageReturnAnimator.setDuration(CARRIAGE_ANIMATION_RETURN_DURATION_MS);
        carriageReturnAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float value = !skipAnimation ?
                        (float) valueAnimator.getAnimatedValue() : 0.01f;
                carriageX = -carriageOffset + (int) (carriageOffset * 2 * value);
                invalidateSelf();
            }
        });

        carriageReturnAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!hasAnimationStarted) {
                    startAnimation();
                    carriageReturnAnimator.setFloatValues(1f, 0.01f);
                } else {
                    carriageAnimator.resume();
                }
            }
        });

        pageAnimator.cancel();
        pageAnimator.setDuration(500);
        pageAnimator.setFloatValues(0, .6f);
        pageAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                pagePercent = !skipAnimation ? (float) valueAnimator.getAnimatedValue() : 0f;
                pageBack.setLevel((int) (2000 + pagePercent * 9000));
                page.setLevel((int) (10000 - pagePercent * 10000));
                invalidateSelf();
            }
        });
        pageAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                hasPageAnimationStarted = false;
                startReturnAnimation();
            }
        });
    }

    @Override
    public void start() {
        resetOrigins();
        animatePage();
    }

    @Override
    public void stop() {
        hasAnimationStarted = false;
        skipAnimation = false;
        cancelAnimation();
        resetOrigins();
    }

    private void startAnimation() {
        hasAnimationStarted = true;
        carriageX = -carriageOffset;
        carriageAnimator.start();
    }

    private void startReturnAnimation() {

        carriageReturnAnimator.start();
    }

    private void cancelAnimation() {
        carriageAnimator.cancel();
        carriageReturnAnimator.cancel();
    }

    private void animatePage() {
        pageAnimator.start();
        hasPageAnimationStarted = true;
    }

    private void resetOrigins() {
        setPercent(0f);
        carriageX = 0;
        page.setLevel(10000);
        pageBack.setLevel(0);
        carriageReturnAnimator.setFloatValues(0.5f, 0.01f);
    }

    @Override
    public void setBounds(int left, int top, int right, int bottom) {
        super.setBounds(left, top, right, backgroundHeight + top);
    }

    @Override
    public boolean isRunning() {
        return hasAnimationStarted;
    }

    @Override
    public void setPercent(float percent, boolean invalidate) {
        setPercent(percent);
        if (invalidate) {
            invalidateSelf();
        }
    }

    private void setPercent(float percent) {
        this.percent = percent;
        if (percent == 0f && carriageAnimator.isRunning()) {
            cancelAnimation();
        }
    }

    @Override
    public void offsetTopAndBottom(int offset) {
        top += offset;
        invalidateSelf();
    }

    void setOffsetTopAndBottom(int offsetTop) {
        top = offsetTop;
        invalidateSelf();
    }

    void setSkipAnimation(boolean skipAnimation) {
        this.skipAnimation = skipAnimation;
    }

    boolean isSkipAnimation() {
        return skipAnimation;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (screenWidth <= 0) return;

        final int saveCount = canvas.save();

        this.canvas = canvas;

        canvas.translate(0, top);
        canvas.clipRect(0, -top, screenWidth, parent.getTotalDragDistance());

        if (percent <= 1) {
            canvas.scale(percent, percent, screenWidth / 2, 0);
        }

        boolean shouldTypeKey = shouldTypeKey() && !carriageReturnAnimator.isRunning();
        if (shouldTypeKey) {
            pressedKey = getRandomKeyNumber();
            if (shouldPressSpace()) {
                pressedKey = SPACE_KEY;
            }
        }
        drawCarriage(shouldTypeKey);
        drawTypewriter();
        drawKeyboard();

        canvas.restoreToCount(saveCount);
    }

    private void drawTypewriter() {
        draw(typewriter, 0,
                (int) getContext().getResources().getDimension(R.dimen.typewriter_padding));
    }

    private void drawKeyboard() {
        draw(keyboard, 0, 0);
        int buttonSize = button.getIntrinsicWidth();
        drawTopLineKeys(buttonSize);
        drawMiddleLineKeys(buttonSize);
        drawBottomLineKeys(buttonSize);
    }

    private void drawTopLineKeys(int buttonSize) {
        int buttonXTranslation = keyboard.getIntrinsicWidth();
        for (int i = 0; i < TOP_LINE_KEYS_NUMBER; i++) {
            buttonXTranslation -= buttonSize * 3;
            draw(pressedKey == i ? buttonPressed : button, buttonXTranslation - (buttonSize),
                    (buttonSize * 3));
        }
    }

    private void drawMiddleLineKeys(int buttonSize) {
        int buttonXTranslation = keyboard.getIntrinsicWidth();
        for (int i = 0; i < MIDDLE_LINE_KEYS_NUMBER; i++) {
            buttonXTranslation -= buttonSize * 3;
            draw(pressedKey - TOP_LINE_KEYS_NUMBER == i ? buttonPressed : button,
                    buttonXTranslation + (int) (buttonSize * 0.5), 0);
        }
    }

    private void drawBottomLineKeys(int buttonSize) {
        int buttonXTranslation = keyboard.getIntrinsicWidth();
        for (int i = 0; i < BOTTOM_LINE_KEYS_NUMBER; i++) {
            buttonXTranslation -= buttonSize * 3;

            if (i < SPACE_START_POSITION || i > SPACE_END_POSITION) {
                final int keysBefore = TOP_LINE_KEYS_NUMBER + MIDDLE_LINE_KEYS_NUMBER;
                draw(pressedKey - keysBefore == i ? buttonPressed : button,
                        buttonXTranslation - (buttonSize), -(buttonSize * 3));
            }
        }
        draw(pressedKey == SPACE_KEY ? spacePressed : space, 0, -(buttonSize * 3));
    }

    private void drawPage() {
        int pageOffsetY = pageOffset;
        if (percent >= 1f) {
            percent = 1f;
        }
        if (!hasAnimationStarted && percent <= 1f) {
            pageOffsetY = ((int) ((page.getIntrinsicHeight()) / percent) + pageOffset);
            if (hasPageAnimationStarted || carriageReturnAnimator.isRunning()) {
                double offsetPercent = pagePercent * 1.15;
                pageOffsetY *= (1 - offsetPercent);
            }
        } else {
            page.setLevel((5000 + 2000 * pageRotateCycle));
            pageOffsetY += pageOffset * pageRotateCycle;
        }

        draw(page, carriageX, pageOffset + pageOffsetY);
        drawText(pageOffsetY);
    }

    private void drawPageBack() {
        if (percent >= 1f) {
            percent = 1f;
        }
        int pageBackOffsetY = pageOffset * 3;
        if ((!hasAnimationStarted) && percent <= 1f) {
            if (hasPageAnimationStarted || carriageReturnAnimator.isRunning()) {
                double offsetPercent = pagePercent * 1.65;
                pageBackOffsetY *= offsetPercent;
            }
        } else {
            pageBack.setLevel((10000 - 3333 * pageRotateCycle));
            pageBackOffsetY -= pageOffset * pageRotateCycle;
        }

        draw(pageBack, pageOffset + carriageX, pageOffset + pageBackOffsetY);
    }

    private void drawText(int offsetY) {
        int letterSize = letter.getIntrinsicHeight();
        for (int j = 0; j <= pageRotateCycle; j++) {
            int letterXTranslation = carriageX + page.getIntrinsicWidth() - letterSize * 2;
            for (int i = 0; i < numberOfLettersOnString[j] - 1; i++) {
                letterXTranslation -= letterOffset.get(j).size() <= i ? 0
                        : letterOffset.get(j).get(i);
                draw(letter, letterXTranslation,
                        (pageOffset * 2) - (int) (letterSize * 3.5) * j + offsetY);
            }
        }
    }

    private void drawCarriage(boolean shouldTypeKey) {
        int imaginaryOffset = -carriageOffset;
        if (hasAnimationStarted) {
            imaginaryOffset += carriageOffset * 2 * (currentStep / 16.6f);
        }

        if (shouldTypeKey) {
            if (currentStep != FIRST_STEP) {
                int motion = imaginaryOffset - carriageX;
                if (pressedKey == SPACE_KEY) {
                    numberOfLettersOnString[pageRotateCycle]--;
                    motion *= 2;
                }
                letterOffset.get(pageRotateCycle).add(motion);
            }
            numberOfLettersOnString[pageRotateCycle]++;
            carriageX = imaginaryOffset;
        }

        drawPageBack();
        for (int i = 0; i < parts.size() - 1; i++) {
            draw(parts.get(i), carriageX, (int) (typewriter.getIntrinsicHeight() / 1.1));
        }
        Drawable lastPart = parts.get(parts.size() - 1);
        int bottomPartOffsetY = (int) (typewriter.getIntrinsicHeight() / 1.1) -
                lastPart.getIntrinsicHeight() * 2;

        drawPage();
        draw(lastPart, carriageX, bottomPartOffsetY);
    }

    private void draw(Drawable drawable, int translationX,
            int translationY) {
        int drawableX = getCenterXWithTranslation(drawable.getIntrinsicWidth() + translationX);
        int drawableY = getCenterYWithTranslation(drawable.getIntrinsicHeight() + translationY)
                + offset;
        drawable.setBounds(drawableX, drawableY,
                drawableX + drawable.getIntrinsicWidth(),
                drawableY + drawable.getIntrinsicHeight());
        drawable.draw(canvas);
    }

    private boolean shouldTypeKey() {
        boolean isNewType = currentStep != lastType;
        lastType = currentStep;
        return hasAnimationStarted && isNewType;
    }

    private boolean shouldPressSpace() {
        return pressedKey != SPACE_KEY && (Math.random() <= 0.15f);
    }

    private int getRandomKeyNumber() {
        return (int) (Math.random() * TOTAL_NUMBER_OF_PHYSICAL_KEYS);
    }

    private int getCenterXWithTranslation(int width) {
        //OX goes (+inf; -inf)
        return canvas.getWidth() / 2 - width / 2;
    }

    private int getCenterYWithTranslation(int height) {
        return parent.getTotalDragDistance() / 2 - height / 2;
    }

    private void resetAnimation() {
        pressedKey = EMPTY_KEY;
        currentStep = FIRST_STEP;
        pageRotateCycle = 0;
        for (int i = 0; i < PAGE_CYCLE; i++) {
            letterOffset.get(i).clear();
            numberOfLettersOnString[i] = 0;
        }
    }
}
