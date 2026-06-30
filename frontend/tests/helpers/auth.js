/**
 * 认证辅助工具
 * 处理登录、注册等认证相关操作
 */

const TEST_USER = {
  account: 'admin@example.com',
  password: '123456'
};

/**
 * 通过 API 注册用户
 */
export async function registerUserViaAPI(request) {
  try {
    const response = await request.post('http://localhost:8080/api/auth/register', {
      data: {
        email: TEST_USER.account,
        password: TEST_USER.password,
        username: 'admin'
      }
    });
    const result = await response.json();
    console.log('注册结果:', result);
    return result;
  } catch (error) {
    console.log('注册请求失败（用户可能已存在）:', error.message);
    return null;
  }
}

/**
 * 通过 UI 登录
 */
export async function loginViaUI(page) {
  // 访问登录页
  await page.goto('/login');
  await page.waitForLoadState('networkidle');
  await page.waitForTimeout(1000);

  // 填写登录表单
  const accountInput = page.locator('input[placeholder*="邮箱"], input[placeholder*="手机号"], .el-input__inner').first();
  await accountInput.fill(TEST_USER.account);

  const passwordInput = page.locator('input[type="password"], input[placeholder*="密码"]').first();
  await passwordInput.fill(TEST_USER.password);

  // 点击登录按钮
  const loginBtn = page.locator('button:has-text("登录"), .login-btn, .el-button--primary').first();
  await loginBtn.click();

  // 等待登录完成
  await page.waitForTimeout(2000);

  // 检查是否登录成功（跳转到 dashboard 或其他页面）
  const currentUrl = page.url();
  if (currentUrl.includes('/login')) {
    // 登录失败，可能需要注册
    return false;
  }

  return true;
}

/**
 * 确保登录状态
 */
export async function ensureLoggedIn(page, request) {
  // 先尝试通过 UI 登录
  let loginSuccess = await loginViaUI(page);

  if (!loginSuccess) {
    console.log('登录失败，尝试注册新用户...');
    // 通过 API 注册
    await registerUserViaAPI(request);
    // 重新登录
    loginSuccess = await loginViaUI(page);
  }

  return loginSuccess;
}

export { TEST_USER };
