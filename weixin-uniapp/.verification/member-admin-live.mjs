import { chromium } from 'playwright'
import fs from 'node:fs/promises'
import assert from 'node:assert/strict'
const state=JSON.parse(await fs.readFile('D:/take-out/local-reports/member-live-browser-state.json','utf8'))
const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe'})
const context=await browser.newContext({viewport:{width:1440,height:960}})
await context.addCookies([{name:'token',value:state.adminToken,domain:'127.0.0.1',path:'/'},{name:'username',value:'junzhou',domain:'127.0.0.1',path:'/'},{name:'user_info',value:encodeURIComponent(JSON.stringify(state.userInfo)),domain:'127.0.0.1',path:'/'}])
const page=await context.newPage();const errors=[],bad=[]
page.on('pageerror',e=>errors.push(e.message))
page.on('response',r=>{if(r.url().includes('/api/')&&r.status()>=400)bad.push({url:new URL(r.url()).pathname,status:r.status()})})
try {
 for(const route of ['list','levels','tags','points','recharge-plans','coupons','settings']) {
  await page.goto('http://127.0.0.1:8889/#/member/'+route)
  await page.locator('.member-page').waitFor({timeout:10000})
  await page.waitForLoadState('networkidle')
  assert.equal(await page.getByRole('button',{name:'会员管理',exact:true}).getAttribute('aria-expanded'),'true')
  assert.ok(!(await page.locator('body').innerText()).includes('加载失败'),route)
  if(route==='list')await page.screenshot({path:'D:/take-out/local-reports/member-live-desktop.png',fullPage:true})
 }
 await page.setViewportSize({width:390,height:844})
 await page.goto('http://127.0.0.1:8889/#/member/list')
 await page.locator('.member-page').waitFor({timeout:10000});await page.waitForLoadState('networkidle')
 const size=await page.evaluate(()=>({scroll:document.documentElement.scrollWidth,width:window.innerWidth}))
 assert.ok(size.scroll<=size.width+1,JSON.stringify(size))
 await page.screenshot({path:'D:/take-out/local-reports/member-live-mobile.png',fullPage:true})
 assert.deepEqual(errors,[]);assert.deepEqual(bad,[])
 assert.equal(await page.getByText('订单提醒连接失败',{exact:true}).count(),0)
 console.log('PASS deployed local admin: seven membership routes, actual login/session/API, desktop/mobile, independent menu, no errors or overflow')
} catch(e) {console.log('DEBUG',JSON.stringify({body:(await page.locator('body').innerText()).slice(0,1200),errors,bad}));throw e}
finally {await browser.close()}
