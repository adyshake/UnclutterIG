package com.tremendo.unclutterig;

import android.content.*;
import android.graphics.Canvas;
import android.provider.ContactsContract;
import android.view.*;
import android.widget.ListAdapter;
import android.widget.ListView;

import static com.tremendo.unclutterig.UnclutterIG.*;
import com.tremendo.unclutterig.util.MediaObjectUtils;
import com.tremendo.unclutterig.util.ResourceUtils;

import java.lang.reflect.*;

import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import static de.robv.android.xposed.XposedHelpers.*;


public class MainFeedHooks {

	private LoadPackageParam lpparam;

	public MainFeedHooks(final LoadPackageParam lpparam) {
		this.lpparam = lpparam;
	}

	// TODO - Clear this cheeky hack that might only work for build - 105.0.0.18.119
	private String mainFeedAdapterClassName = "X.1HU";

	protected void doHooks() {
		doStoryHooks();
		doMainFeedHooks();
	}

	protected void doStoryHooks() {
		Method setAdapterMethod = null;
		setAdapterMethod = findMethodExact(ListView.class, "setAdapter", ListAdapter.class);
		if (setAdapterMethod != null) {
			XposedBridge.hookMethod(setAdapterMethod, new XC_MethodHook() {
				@Override
				public void beforeHookedMethod(final MethodHookParam param) throws Throwable {
					Object adapter = param.args[0];

					if (adapter == null) {
						return;
					}
					if (isOnMainPage(param.thisObject, adapter.getClass())) {
						if (shouldHideStories()) {
							param.setResult(null);
						}
					}
				}
			});
		}
	}

	protected void doMainFeedHooks() {
		String feedViewClassName = findFeedViewClassName(lpparam.classLoader);

		if (feedViewClassName == null) {
			errorLog("Couldn't determine 'main feed' class to hide sponsored content");
			return;
		}

		Class<?> FeedViewClass = findClass(feedViewClassName, lpparam.classLoader);
		Method[] methodsToHook = findMethodsByExactParameters(FeedViewClass, View.class, int.class, View.class, ViewGroup.class, Object.class, Object.class);

		if (methodsToHook.length == 0) {
			errorLog("Couldn't find method within 'main feed' class to hide sponsored content");
			return;
		}

		Method feedItemViewMethod = methodsToHook[0];

		XposedBridge.hookMethod(feedItemViewMethod, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				View feedItemView = (View) param.getResult();

				if (shouldHideMainFeed()) {
					showFeedItemView(feedItemView, false);
					return;
				}

				Object mediaObjectInFeedItem = param.args[3];

				if (mediaObjectInFeedItem != null) {

					if (shouldHideAds()) {
						if (MediaObjectUtils.isSponsoredContent(mediaObjectInFeedItem)) {
							showFeedItemView(feedItemView, false);
							return;
						}
					}

					if (shouldHidePaidPartnershipPosts()) {
						if (MediaObjectUtils.isPaidPartnershipContent(mediaObjectInFeedItem)) {
							showFeedItemView(feedItemView, false);
							return;
						}
					}
				}

				showFeedItemView(feedItemView, true);
			}
		});
	}

	private String getMainFeedAdapterClassName() {
		return mainFeedAdapterClassName;
	}

	private static boolean hasMainPageIcon(View rootView) {
		// TODO - Find a resource that actually exists and works on the main page
		//        because this one does not
		return (rootView.findViewById(ResourceUtils.getId("unread_count_text_view")) != null);
	}

	private static boolean isMainPagePrimaryFeed(View adapterHolderView) {
		return hasMainPageIcon(adapterHolderView.getRootView());
	}

	private boolean isOnMainPage(Object adapterContainer, Class<?> adapterClass) {
		if (getMainFeedAdapterClassName() != null) {
			return getMainFeedAdapterClassName().equals(adapterClass.getName());
		}

		View rootView = ((View) adapterContainer).getRootView();
		if (isMainPagePrimaryFeed(rootView)) {
			mainFeedAdapterClassName = adapterClass.getName();
			return true;
		}

		return false;
	}

	/* 
	 *   Scans field types in the third parameter of LoadMoreButton's method 'setViewType(LoadMoreButton, ?, *?*)'.
	 *   The relevant field type will contain a Context field and 'getViewTypeCount' method
	 */
	private String findFeedViewClassName(ClassLoader classLoader) {
		try {
			ClassToScan LoadMoreButtonClass = ClassToScan.find("com.instagram.ui.widget.loadmore.LoadMoreButton", classLoader);
			Method setViewTypeMethod = LoadMoreButtonClass.findMethodByName("setViewType");

			if (setViewTypeMethod != null && setViewTypeMethod.getParameterTypes().length > 2) {
				Class<?> ParameterContainingRelevantFieldType = setViewTypeMethod.getParameterTypes()[2];

				for (Field declaredField: ParameterContainingRelevantFieldType.getDeclaredFields()) {
					ClassToScan DeclaredFieldType = new ClassToScan(declaredField.getType());

					if (DeclaredFieldType.findMethodByName("getViewTypeCount") != null && DeclaredFieldType.hasFieldType(Context.class)) {
						return DeclaredFieldType.getName();
					}
				}
			}
		} catch (XposedHelpers.ClassNotFoundError e) {
			XposedBridge.log("Unclutter IG: Couldn't find 'LoadMoreButton' class. Not able to scan its methods to search for 'main feed' class name");
		}

		return null;
	}

	private void showFeedItemView(View view, boolean setVisible) {
		ViewGroup.LayoutParams layoutParams = view.getLayoutParams();

		if (layoutParams == null) {
			layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		}

		if (setVisible) {
			layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
			view.setVisibility(View.VISIBLE);
		} else {
			layoutParams.height = 1;
			view.setVisibility(View.GONE);
		}
		view.setLayoutParams(layoutParams);
	}
}
