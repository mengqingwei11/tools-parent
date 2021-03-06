package com.kalix.tools.command;

import com.kalix.framework.core.api.web.IApplication;
import com.kalix.framework.core.api.web.IMenu;
import com.kalix.framework.core.api.web.IModule;
import com.kalix.framework.core.util.ScriptRunner;
import com.kalix.framework.core.web.manager.ApplicationManager;
import com.kalix.framework.core.web.manager.MenuManager;
import com.kalix.framework.core.web.manager.ModuleManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by Administrator on 2016-08-03.
 * 初始化权限
 */
public class PermissionInit {
    private String appSql = "INSERT INTO public.sys_application " +
            "(id, createby, creationdate, updateby, updatedate, code, location, name ,iconCls ,remark, version_, supportmobile) " +
            "VALUES ('%s', '管理员', '%s', '管理员', '%s', '%s', '', '%s', '%s', '%s', '1', '%s');";
    private String appClearSql = "DELETE FROM public.sys_application;";

    private String funSql = "INSERT INTO public.sys_function " +
            "(id, createby, creationdate, updateby, updatedate, applicationid, code, isleaf, name, parentid, permission, remark, version_, dataPermission, dataPermissionKey) " +
            "VALUES ('%s', '管理员', '%s', '管理员', '%s', '%s', '%s', '%s', '%s', '%s', '%s', '', '1', '%s', '%s');";
    private String funClearSql = "DELETE FROM public.sys_function;";

    private String role_funClearSql = "DELETE FROM public.sys_role_function where roleid=1;";
    private String app_funClearSql = "DELETE FROM public.sys_role_application where roleid=1;";

    private String role_funSql = "INSERT INTO public.sys_role_function " +
            "(id, createby, creationdate, updateby, updatedate, functionid, roleid, version_) " +
            "VALUES ('%s', '管理员', '%s', '管理员', '%s', '%s', '1', '1');";
    private String role_applicationsql = "INSERT INTO public.sys_role_application " +
            "(id, createby, creationdate, updateby, updatedate, applicationid, roleid, version_) " +
            "VALUES ('%s', '管理员', '%s', '管理员', '%s','%s', '1',  '1');";
    // 数据授权清理脚本
    private String dataAuthClearSql = "update sys_function set datapermission=false where applicationid=1;";

    String strNow = Util.getNowString();

    //get datasource
    DataSource dataSource = Util.getKalixDataSource();
    //run script
    ScriptRunner scriptRunner = new ScriptRunner(dataSource, false, true);

    public void init() {
        List<IApplication> applicationList = ApplicationManager.getInstall().getApplicationList();
        if (applicationList != null && !applicationList.isEmpty()) {
            int appId = 0;
            int moduleId = 1000;
            beforeClearData();//清空表数据
            for (IApplication application : applicationList) {
                ++appId;
                insertApplication(appId, application);
                List<IModule> moduleList = ModuleManager.getInstall().getModuleList(application.getId());
                if (moduleList != null && !moduleList.isEmpty()) {
                    for (IModule module : moduleList) {
                        ++moduleId;
                        insertModule(appId, moduleId, module);
                        List<IMenu> allMenu = MenuManager.getInstall().getMenuList(module.getId());
                        if (allMenu != null && !allMenu.isEmpty()) {
                            int menuId = moduleId;
                            for (IMenu menu : allMenu) {
                                moduleId = insertMenu(appId, moduleId, menuId, menu);
                            }
                        }
                    }
                }
            }
            insertApplicationPermit(appId);
            insertFunctionPermit(moduleId);
            afterClearData();
        }
    }

    /**
     * 增加application授权信息
     *
     * @param appId
     */
    private void insertApplicationPermit(int appId) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1; i <= appId; i++) {
            String str = String.format(role_applicationsql, String.valueOf(i), strNow, strNow, String.valueOf(i));
            builder.append(str);
        }
        try {
            StringReader reader = new StringReader(builder.toString());
            Util.outPrint("insert role application data ");
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 增加function授权信息
     *
     * @param moduleId
     */
    private void insertFunctionPermit(int moduleId) {
        StringBuilder builder = new StringBuilder();
        for (int i = 1000; i <= moduleId; i++) {
            String str = String.format(role_funSql, String.valueOf(i), strNow, strNow, String.valueOf(i));
            builder.append(str);
        }
        try {
            /*InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream("admin_all.sql");
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            scriptRunner.runScript(br);*/

            StringReader reader = new StringReader(builder.toString());
            Util.outPrint("insert role function data ");
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }


    }


    /**
     * 插入menu数据
     *
     * @param appId
     * @param moduleId
     * @param menu
     */
    private int insertMenu(int appId, int moduleId, int menuId, IMenu menu) {

        ++moduleId;
        String str = menu.getId();
        String dataPermission = str.substring(0, str.length() - 4).toLowerCase(); // 处理数据权限的字符串,去掉结尾的menu
        String menuStr = String.format(funSql, String.valueOf(moduleId), strNow, strNow, String.valueOf(appId),
                menu.getId(), "0", menu.getText(), String.valueOf(menuId), menu.getPermission(), "1", dataPermission); // 格式化字符串
        StringReader reader = new StringReader(menuStr);
        try {
            Util.outPrint("insert menu data of " + menu.getId());
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        //處理菜單下的按鈕
        //數據格式 "新增,add;刪除,delete;修改,edit;查看,view";
        Integer btnParentId = new Integer(moduleId);
        String strBtns = menu.getButtons();
        StringBuilder build = null;
        //when button value equal null it means no buttons has permission
        if (!strBtns.equals("null")) {
            String[] btns = strBtns.split(";");
            build = new StringBuilder();
            for (String btn : btns) {
                String[] values = btn.split(",");
                String strName = values[0];
                String strKey = values[1];
                String btnStr = String.format(funSql, String.valueOf(++moduleId), strNow, strNow, String.valueOf(appId),
                        strKey, "1", strName, String.valueOf(btnParentId), menu.getPermission() + ":" + strKey, "0", ""); // 格式化字符串
                build.append(btnStr);
            }
            StringReader btnReader = new StringReader(build.toString());
            try {
                Util.outPrint("insert button data of " + menu.getId());
                scriptRunner.runScript(btnReader);
            } catch (IOException e) {
                e.printStackTrace();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return moduleId;
    }

    /**
     * 插入module数据
     *
     * @param appId
     * @param appId
     */
    private void insertModule(int appId, int moduleId, IModule module) {
        String str = String.format(funSql, String.valueOf(moduleId), strNow, strNow, String.valueOf(appId),
                module.getId(), "0", module.getText(), "-1", module.getPermission(), "0", ""); // 格式化字符串
        StringReader reader = new StringReader(str);
        try {
            Util.outPrint("insert module data of " + module.getId());
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * 插入application数据
     *
     * @param appId       数据库id
     * @param application
     */
    private void insertApplication(int appId, IApplication application) {
        String str = String.format(appSql, String.valueOf(appId), strNow, strNow, application.getId(), application.getText(), application.getIconCls(), application.getText(), application.getSupportMobile()); // 格式化字符串
        StringReader reader = new StringReader(str);
        try {
            Util.outPrint("insert application data of " + application.getId());
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * clear data of db
     */
    private void beforeClearData() {
        StringReader reader = new StringReader(appClearSql + funClearSql + role_funClearSql + app_funClearSql);
        try {
            Util.outPrint("clean application data!");
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * after clear data of db
     */
    private void afterClearData() {
        StringReader reader = new StringReader(dataAuthClearSql);
        try {
            Util.outPrint("after clean application data!");
            scriptRunner.runScript(reader);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}
