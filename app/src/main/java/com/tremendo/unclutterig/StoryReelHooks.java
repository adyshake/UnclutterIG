package com.tremendo.unclutterig;

import static com.tremendo.unclutterig.UnclutterIG.*;
import com.tremendo.unclutterig.util.MediaObjectUtils;
import java.lang.reflect.*;
import java.util.*;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import static de.robv.android.xposed.XposedHelpers.*;

public class StoryReelHooks {
	private LoadPackageParam lpparam;

	public StoryReelHooks(final LoadPackageParam lpparam) {
		this.lpparam = lpparam;
	}

	protected void doHooks() {

		final String reelClassName = findReelClassName();

		if (reelClassName == null) {
			errorLog("Couldn't determine 'reel' class name to skip sponsored content in stories");
			return;
		}

		Class<?> ReelClass = findClass(reelClassName, lpparam.classLoader);
		Method reelListMethod = findReelListMethod(ReelClass);

		if (reelListMethod == null) {
			errorLog("Couldn't find method for handling story reel's content list to remove sponsored content");
			return;
		}

		XposedBridge.hookMethod(reelListMethod, new XC_MethodHook() {
			@Override
			public void beforeHookedMethod(final MethodHookParam param) throws Throwable {
				List storyReelMediaList = (List) param.args[0];

				if (shouldHideStories()) {
					storyReelMediaList.clear();
					return;
				}

				if (!shouldHideAds() && !shouldHidePaidPartnershipPosts()) {
					return;
				}

				if (storyReelMediaList == null || storyReelMediaList.isEmpty()) {
					return;
				}

				if (shouldHideAds()) {
					Object mediaObjectInStoryReel = storyReelMediaList.get(0);

					if (MediaObjectUtils.isSponsoredContent(mediaObjectInStoryReel)) {
						storyReelMediaList.clear();
						return;
					}
				}

				if (shouldHidePaidPartnershipPosts()) {
					for (int index = storyReelMediaList.size()-1; index >= 0; index--) {
						Object mediaObjectInStoryReel = storyReelMediaList.get(index);

						if (MediaObjectUtils.isPaidPartnershipContent(mediaObjectInStoryReel)) {
							storyReelMediaList.remove(index);
						}
					}
				}
			}
		});
	}

	/*
	 *   Should be a method in ReelViewerFragment with the targeted 'reel' class as second parameter in signature: (ReelViewerFragment, ?, String, Integer)
	 */
	private String findReelClassName() {
		try {
			Class<?> ReelViewerFragmentClass = findClass("com.instagram.reels.fragment.ReelViewerFragment", lpparam.classLoader);

			for (Method method : ReelViewerFragmentClass.getDeclaredMethods()) {
				Class[] parameterTypes = method.getParameterTypes();

				if (parameterTypes.length == 4) {
					if (parameterTypes[0] == ReelViewerFragmentClass && parameterTypes[2] == String.class && parameterTypes[3] == Integer.class) {
						return parameterTypes[1].getName();
					}
				}
			}
		} catch (XposedHelpers.ClassNotFoundError e) {
			XposedBridge.log("Unclutter IG: Couldn't find 'ReelViewerFragment' class. Not able to scan its methods to search for 'story reel' class name");
		}

		return null;
	}

	private static Method findReelListMethod(Class<?> ReelClass) {
		Method[] methods = findMethodsByExactParameters(ReelClass, void.class, List.class);

		if (methods.length > 0) {
			return methods[0];
		}

		return null;
	}
}