/*
 * BRLTTY - A background process providing access to the console screen (when in
 *          text mode) for a blind person using a refreshable braille display.
 *
 * Copyright (C) 1995-2013 by The BRLTTY Developers.
 *
 * BRLTTY comes with ABSOLUTELY NO WARRANTY.
 *
 * This is free software, placed under the terms of the
 * GNU General Public License, as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any
 * later version. Please see the file LICENSE-GPL for details.
 *
 * Web Page: http://mielke.cc/brltty/
 *
 * This software is maintained by Dave Mielke <dave@mielke.cc>.
 */

package org.a11y.brltty.android;

import android.util.Log;

import android.os.Build;
import android.os.Bundle;

import android.view.accessibility.AccessibilityNodeInfo;
import android.graphics.Rect;
import android.view.KeyEvent;

public class RealScreenElement extends ScreenElement {
  private static final String LOG_TAG = RealScreenElement.class.getName();

  private final AccessibilityNodeInfo accessibilityNode;

  @Override
  public Rect getVisualLocation () {
    synchronized (this) {
      if (visualLocation == null) {
        visualLocation = new Rect();
        accessibilityNode.getBoundsInScreen(visualLocation);
      }
    }

    return visualLocation;
  }

  @Override
  public AccessibilityNodeInfo getAccessibilityNode () {
    return AccessibilityNodeInfo.obtain(accessibilityNode);
  }

  @Override
  public boolean isCheckable () {
    return accessibilityNode.isCheckable();
  }

  @Override
  public boolean isChecked () {
    return accessibilityNode.isChecked();
  }

  @Override
  public boolean isEditable () {
    return ReflectionHelper.canAssign(android.widget.EditText.class, accessibilityNode.getClassName().toString());
  }

  @Override
  public boolean performAction (int offset) {
    ScreenTextEditor editor = ScreenTextEditor.getIfFocused(accessibilityNode);

    if (editor != null) {
      if (!onBringCursor()) return false;
      return ScreenDriver.inputCursor(offset);
    }

    return super.performAction(offset);
  }

  @Override
  protected String makeBrailleText (String text) {
    text = super.makeBrailleText(text);

    if (ScreenTextEditor.getIfFocused(accessibilityNode) != null) {
      text += ' ';
    }

    return text;
  }

  private AccessibilityNodeInfo getActionableNode (int action) {
    AccessibilityNodeInfo node = getAccessibilityNode();
    Rect inner = getVisualLocation();

    while (true) {
      if (node.isEnabled()) {
        if ((node.getActions() & action) != 0) {
          return node;
        }
      }

      AccessibilityNodeInfo parent = node.getParent();
      if (parent == null) break;

      Rect outer = new Rect();
      parent.getBoundsInScreen(outer);

      if (!outer.contains(inner)) {
        parent.recycle();
        parent = null;
        break;
      }

      inner = outer;
      node.recycle();
      node = parent;
    }

    node.recycle();
    node = null;
    return null;
  }

  private boolean doAction (int action) {
    AccessibilityNodeInfo node = getActionableNode(action);
    if (node == null) return false;

    boolean performed = node.performAction(action);
    node.recycle();
    node = null;
    return performed;
  }

  public boolean doKey (int keyCode, boolean longPress) {
    boolean done = false;
    final int ACTION_CLAIM = AccessibilityNodeInfo.ACTION_FOCUS;
    final int ACTION_RELEASE = AccessibilityNodeInfo.ACTION_CLEAR_FOCUS;
    AccessibilityNodeInfo node = getActionableNode(ACTION_CLAIM | ACTION_RELEASE);

    if (node != null) {
      if (node.isFocused()) {
        if (ScreenDriver.inputKey(keyCode, longPress)) done = true;
      } else if (node.performAction(ACTION_CLAIM)) {
        final long start = System.currentTimeMillis();

        while (true) {
          if (ScreenDriver.inputKey(keyCode, longPress)) {
            done = true;
            break;
          }

          if ((System.currentTimeMillis() - start) >= ApplicationParameters.keyRetryTimeout) {
            break;
          }

          try {
            Thread.sleep(ApplicationParameters.keyRetryInterval);
          } catch (InterruptedException exception) {
          }
        }
      }

      node.recycle();
      node = null;
    }

    return done;
  }

  @Override
  public boolean onBringCursor () {
    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
      {
        AccessibilityNodeInfo focusedNode = accessibilityNode.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY);

        if (focusedNode != null) {
          boolean done = focusedNode.equals(accessibilityNode);
          focusedNode.recycle();
          focusedNode = null;
          if (done) return true;
        }
      }

      return doAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS);
    }

    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
      if (accessibilityNode.isFocused()) return true;
      return doAction(AccessibilityNodeInfo.ACTION_FOCUS);
    }

    return super.onBringCursor();
  }

  @Override
  public boolean onClick () {
    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
      if (isEditable()) {
        return doAction(AccessibilityNodeInfo.ACTION_FOCUS);
      }
    }

    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
      return doAction(AccessibilityNodeInfo.ACTION_CLICK);
    }

    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
      return doKey(KeyEvent.KEYCODE_DPAD_CENTER, false);
    }

    return super.onClick();
  }

  @Override
  public boolean onLongClick () {
    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
      return doAction(AccessibilityNodeInfo.ACTION_LONG_CLICK);
    }

    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
      return doKey(KeyEvent.KEYCODE_DPAD_CENTER, true);
    }

    return super.onLongClick();
  }

  @Override
  public boolean onScrollBackward () {
    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
      return doAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD);
    }

    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
      return doKey(KeyEvent.KEYCODE_PAGE_UP, false);
    }

    return super.onScrollBackward();
  }

  @Override
  public boolean onScrollForward () {
    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.JELLY_BEAN)) {
      return doAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
    }

    if (ApplicationUtilities.haveSdkVersion(Build.VERSION_CODES.ICE_CREAM_SANDWICH)) {
      return doKey(KeyEvent.KEYCODE_PAGE_DOWN, false);
    }

    return super.onScrollForward();
  }

  public RealScreenElement (String text, AccessibilityNodeInfo node) {
    super(text);
    accessibilityNode = AccessibilityNodeInfo.obtain(node);

    if (isEditable()) {
      ScreenTextEditor.get(accessibilityNode, true);
    }
  }
}
