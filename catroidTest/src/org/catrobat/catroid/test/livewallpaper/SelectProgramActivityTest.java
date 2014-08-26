/*
 * Catroid: An on-device visual programming system for Android devices
 * Copyright (C) 2010-2014 The Catrobat Team
 * (<http://developer.catrobat.org/credits>)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * An additional term exception under section 7 of the GNU Affero
 * General Public License, version 3, is available at
 * http://developer.catrobat.org/license_additional_term
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.catrobat.catroid.test.livewallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.test.SingleLaunchActivityTestCase;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.jayway.android.robotium.solo.Solo;

import org.catrobat.catroid.ProjectManager;
import org.catrobat.catroid.R;
import org.catrobat.catroid.common.Constants;
import org.catrobat.catroid.common.ProjectData;
import org.catrobat.catroid.common.ScreenValues;
import org.catrobat.catroid.common.StandardProjectHandler;
import org.catrobat.catroid.content.Project;
import org.catrobat.catroid.io.StorageHandler;
import org.catrobat.catroid.livewallpaper.LiveWallpaper;
import org.catrobat.catroid.livewallpaper.ProjectManagerState;
import org.catrobat.catroid.livewallpaper.ui.SelectProgramActivity;
import org.catrobat.catroid.test.livewallpaper.utils.TestUtils;
import org.catrobat.catroid.uitest.util.UiTestUtils;
import org.catrobat.catroid.utils.Utils;

import java.io.File;
import java.util.List;
import java.util.Locale;


public class SelectProgramActivityTest extends
		SingleLaunchActivityTestCase<SelectProgramActivity> {

	private static final String TEST_PROJECT_NAME = "Test project";
	private static final String PACKAGE = "org.catrobat.catroid";
	private Solo solo;
	private Project testProject;
	private LiveWallpaper lwp = LiveWallpaper.getInstance();
	private ProjectManager projectManager = ProjectManager.getInstance(ProjectManagerState.LWP);
	private File lookFile;

	public SelectProgramActivityTest() {
		super(PACKAGE,SelectProgramActivity.class);
	}


	protected void setUp() throws Exception {
		super.setUp();
		UiTestUtils.prepareStageForTest();

		solo = new Solo(getInstrumentation(),getActivity());
		DisplayMetrics disp = new DisplayMetrics();
		getActivity().getApplicationContext();
		((WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getMetrics(disp);
		ScreenValues.SCREEN_HEIGHT = disp.heightPixels;
		ScreenValues.SCREEN_WIDTH = disp.widthPixels;

		Log.v("LWP", String.valueOf(ScreenValues.SCREEN_HEIGHT + " " + String.valueOf(ScreenValues.SCREEN_WIDTH)));
		if(projectManager.getCurrentProject() == null || projectManager.getCurrentProject().getName()!= solo.getString(R.string.default_project_name)){
			Project defaultProject;
			try{
				defaultProject = StandardProjectHandler.createAndSaveStandardProject(getActivity().getApplicationContext());
			}
			catch(IllegalArgumentException e){
				Log.d("LWP", "The default project was not created because it probably already exists");
				defaultProject = StorageHandler.getInstance().loadProject(solo.getString(R.string.default_project_name));
			}
			ProjectManager.getInstance(ProjectManagerState.LWP).setProject(defaultProject);
		}
		TestUtils.restartActivity(getActivity());
	//	testProject = TestUtils.createEmptyProjectWithoutSettingIt(getActivity().getApplicationContext(), TEST_PROJECT_NAME);

//		lwp.onCreate();


	}

	protected void tearDown() throws Exception {
		//StorageHandler.getInstance().deleteProject(testProject);
		super.tearDown();
	}

	public void testAboutDialog()
	{
		solo.clickOnActionBarItem(R.id.about);
		assertTrue("About pocket code text not found", solo.searchText(solo.getString(R.string.dialog_about_license_info)));
		assertTrue("About pocket code link not found", solo.searchText(solo.getString(R.string.dialog_about_catrobat_link_text)));
		assertTrue("About pocket code version not found", solo.searchText(Utils.getVersionName(getActivity().getApplicationContext())));
		solo.goBack();
	}


	public void testWallpaperSelection()
	{
		assertEquals("The current project should be set to the standard project", solo.getString(R.string.default_project_name), projectManager.getCurrentProject().getName());

		solo.clickOnText(TEST_PROJECT_NAME);
		solo.sleep(200);
		solo.clickOnText(solo.getString(R.string.yes));
		solo.sleep(2000);

		String currentProjectName = projectManager.getCurrentProject().getName();
		assertTrue("The project was not successfully changed", currentProjectName.equals(TEST_PROJECT_NAME));
	}


	public void testDeleteSingleProject(){
		solo.sleep(500);
		SelectProgramActivity selectProgramActvity = (SelectProgramActivity) solo.getCurrentActivity();
		List<ProjectData> projectList = selectProgramActvity.getSelectProgramFragment().getProjectList();
		int initialProgramCount = projectList.size();

		solo.clickOnActionBarItem(R.id.delete);
		solo.clickOnText(TEST_PROJECT_NAME);

		clickOnOkayActionBarItem(solo);

		solo.clickOnText(solo.getString(R.string.yes));
		assertFalse("The project was not deleted", solo.searchText(TEST_PROJECT_NAME));

		projectList = selectProgramActvity.getSelectProgramFragment().getProjectList();
		int expectedProgramCountAfterDeletion = initialProgramCount - 1;
		assertEquals("The program count not okay after deleting one program", expectedProgramCountAfterDeletion, projectList.size());
	}

	public void testDeleteCurrentProject(){
		assertEquals("The current project should be set to the standard project", solo.getString(R.string.default_project_name), projectManager.getCurrentProject().getName());
		solo.clickOnActionBarItem(R.id.delete);
		solo.clickOnText(solo.getString(R.string.default_project_name));
		clickOnOkayActionBarItem(solo);
		assertTrue("The error dialog was not shown", solo.searchText(solo.getString(R.string.lwp_error_delete_current_program)));
	}

	public void testDeleteAllProjects(){
		SelectProgramActivity selectProgramActvity = (SelectProgramActivity) solo.getCurrentActivity();
		List<ProjectData> projectList = selectProgramActvity.getSelectProgramFragment().getProjectList();
		int initialProgramCount = projectList.size();

		solo.clickOnActionBarItem(R.id.delete);
		String selectAll = solo.getString(R.string.select_all).toUpperCase(Locale.getDefault());
		solo.clickOnText(selectAll);
		clickOnOkayActionBarItem(solo);

		assertTrue("The error dialog for deleting all projects but the current one was not shown",
				solo.searchText(solo.getString(R.string.lwp_error_delete_multiple_program)));
		solo.clickOnButton(solo.getString(R.string.yes));

		assertTrue("The confirmation dialog for deleting projects was not shown",
				solo.searchText(solo.getString(R.string.dialog_confirm_delete_multiple_programs_title)));

		assertTrue("The title of the confirmation dialog for deleting projects was not shown",
				solo.searchText(solo.getString(R.string.dialog_confirm_delete_program_message)));

		solo.clickOnButton(solo.getString(R.string.no));
		assertTrue("The program count is not equal to program count before clicking on delete", projectList.size() == initialProgramCount);

		solo.clickOnActionBarItem(R.id.delete);
		solo.clickOnText(selectAll);
		clickOnOkayActionBarItem(solo);
		solo.clickOnText(solo.getString(R.string.yes));
		solo.clickOnText(solo.getString(R.string.yes));

		solo.sleep(2000);
		selectProgramActvity = (SelectProgramActivity) solo.getCurrentActivity();
		projectList = selectProgramActvity.getSelectProgramFragment().getProjectList();
		assertTrue("The program count should be 1 after delete all projects but the current one but was " + projectList.size(), projectList.size() == 1);

	}

	public void testEnableSoundCheckBox(){
		solo.clickOnText(TEST_PROJECT_NAME);
		assertTrue("The set program dialog was not found", solo.searchText(solo.getString(R.string.lwp_confirm_set_program_message)));
		assertTrue("The enable sound text was not found", solo.searchText(solo.getString(R.string.lwp_enable_sound)));

		SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
		Editor editor = sharedPreferences.edit();
		editor.putBoolean(Constants.PREF_SOUND_DISABLED, true);
		editor.commit();

		solo.clickOnText(solo.getString(R.string.lwp_enable_sound));

		assertFalse("The sound should have been enabled but it's not", sharedPreferences.getBoolean(Constants.PREF_SOUND_DISABLED, true));

		solo.clickOnText(solo.getString(R.string.lwp_enable_sound));

		assertTrue("The sound should have been disabled but it's not", sharedPreferences.getBoolean(Constants.PREF_SOUND_DISABLED, false));
	}

	private void clickOnOkayActionBarItem(Solo solo){

		float width = (9.0f * (float)ScreenValues.SCREEN_WIDTH)/100.0f;
		float height = (7.0f * (float)ScreenValues.SCREEN_HEIGHT)/100.0f;

		solo.clickOnScreen(width, height);
	}

}


