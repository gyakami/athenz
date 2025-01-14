/*
 * Copyright The Athenz Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

describe('Domain', () => {
    it('should successfully add domain point of contact and security poc', async () => {
        await browser.newUser();
        await browser.url(`/`);
        await expect(browser).toHaveUrlContaining('athenz');

        let testDomain = await $('a*=athenz.dev.functional-test');
        await browser.waitUntil(async () => await testDomain.isClickable());
        await testDomain.click();

        // test adding poc
        let pocAnchor = await $('a[data-testid="poc-link"]');
        await browser.waitUntil(async () => await pocAnchor.isClickable());
        await pocAnchor.click();
        let userInput = await $('input[name="poc-name"]');
        await userInput.addValue('craman');
        let userOption = await $('div*=Chandu Raman [user.craman]');
        await userOption.click();
        let submitButton = await $('button*=Submit');
        await submitButton.click();
        await expect(pocAnchor).toHaveTextContaining('Chandu Raman');

        // test adding security poc
        let securityPocAnchor = await $('a[data-testid="security-poc-link"]');
        await browser.waitUntil(
            async () => await securityPocAnchor.isClickable()
        );
        await securityPocAnchor.click();
        userInput = await $('input[name="poc-name"]');
        await userInput.addValue('craman');
        userOption = await $('div*=Chandu Raman [user.craman]');
        await userOption.click();
        submitButton = await $('button*=Submit');
        await submitButton.click();
        await expect(securityPocAnchor).toHaveTextContaining('Chandu Raman');
    });

    it('modal to add business service - should preserve input on blur, make input bold when selected in dropdown, reject unselected input, allow submission of empty input', async () => {
        await browser.newUser();
        await browser.url(`/domain/athenz.dev.functional-test/role`);
        await expect(browser).toHaveUrlContaining('athenz');

        // expand domain details
        let expand = await $(
            `.//*[local-name()="svg" and @data-wdio="domain-details-expand-icon"]`
        );
        await browser.waitUntil(async () => await expand.isClickable());
        await expand.click();

        // click add business service
        let addBusinessService = await $(
            'a[data-testid="add-business-service"]'
        );
        await browser.waitUntil(
            async () => await addBusinessService.isClickable()
        );
        await addBusinessService.click();

        await browser.pause(2000); // wait to make sure dropdown options are loaded

        // add random text to modal input
        let bsInput = await $('input[name="business-service-drop"]');
        await bsInput.addValue('nonexistent.service');

        // blur
        await browser.keys('Tab');

        // input did not change
        expect(await bsInput.getValue()).toBe('nonexistent.service');

        // input is not bold
        let fontWeight = await bsInput.getCSSProperty('font-weight').value;
        expect(fontWeight).toBeUndefined();

        // submit (item in dropdown is not selected)
        let submitButton = await $('button*=Submit');
        await submitButton.click();

        // verify error message
        let errorMessage = await $('div[data-testid="error-message"]');
        expect(await errorMessage.getText()).toBe(
            'Business Service must be selected in the dropdown or clear input before submitting'
        );

        // unclick checkbox to allow selection of business services not associated with current account
        let checkbox = await $('input[id="checkbox-show-all-bservices"]');
        await browser.execute(function (checkboxElem) {
            checkboxElem.click();
        }, checkbox);

        // type valid input and select item in dropdown
        let clearInput = await $(
            `.//*[local-name()="svg" and @data-wdio="clear-input"]`
        );
        await clearInput.click();
        // make dropdown visible
        await bsInput.click();
        await bsInput.addValue('PolicyEnforcementService.GLB');
        let dropdownOption = await $(
            '//div[contains(text(), "PolicyEnforcementService.GLB")]'
        );
        await dropdownOption.click();

        // verify input contains pes service
        expect(await bsInput.getValue()).toBe('PolicyEnforcementService.GLB');

        // verify input is in bold
        fontWeight = await bsInput.getCSSProperty('font-weight');
        expect(fontWeight.value === 700).toBe(true);

        // submit
        submitButton = await $('button*=Submit');
        await submitButton.click();

        // business service can be seen added to domain
        addBusinessService = await $('a[data-testid="add-business-service"]');
        await expect(addBusinessService).toHaveTextContaining(
            'PolicyEnforcementService.GLB'
        );

        // click add business service
        await browser.waitUntil(
            async () => await addBusinessService.isClickable()
        );
        await addBusinessService.click();

        // clear current input
        clearInput = await $(
            `.//*[local-name()="svg" and @data-wdio="clear-input"]`
        );
        await browser.waitUntil(async () => await clearInput.isClickable());
        await clearInput.click();

        // submit empty input
        submitButton = await $('button*=Submit');
        await submitButton.click();

        // business service for the domain should be empty
        await browser.waitUntil(
            async () => await addBusinessService.isClickable()
        );
        expect(addBusinessService).toHaveTextContaining('add');
    });

    it('Manage Domains - modal to change add business service - should preserve input on blur, make input bold when selected in dropdown, reject unselected input', async () => {
        await browser.newUser();

        // open athenz manage domains page
        await browser.url(`/domain/manage`);
        await expect(browser).toHaveUrlContaining('athenz');

        // click add business service
        let addBusinessService = await $(
            'a[data-testid="business-service-athenz.dev.functional-test"]'
        );
        await browser.waitUntil(
            async () => await addBusinessService.isClickable()
        );
        await addBusinessService.click();

        await browser.pause(4000); // wait to make sure dropdown options are loaded

        // add random text
        let bsInput = await $('input[name="business-service-drop"]');
        await bsInput.addValue('nonexistent.service');

        // blur
        await browser.keys('Tab');

        // input did not change
        expect(await bsInput.getValue()).toBe('nonexistent.service');

        // input is not bold
        let fontWeight = await bsInput.getCSSProperty('font-weight').value;
        expect(fontWeight).toBeUndefined();

        // submit (item in dropdown is not selected)
        let submitButton = await $('button*=Submit');
        await submitButton.click();

        // verify error message
        let errorMessage = await $('div[data-testid="error-message"]');
        expect(await errorMessage.getText()).toBe(
            'Business Service must be selected in the dropdown'
        );

        let clearInput = await $(
            `.//*[local-name()="svg" and @data-wdio="clear-input"]`
        );
        await clearInput.click();

        let checkbox = await $('input[id="checkbox-show-all-bservices"]');
        await browser.execute(function (checkboxElem) {
            checkboxElem.click();
        }, checkbox);

        // make dropdown visible
        await bsInput.click();
        // type valid input and select item in dropdown
        await bsInput.addValue('PolicyEnforcementService.GLB');
        let dropdownOption = await $('div*=PolicyEnforcementService.GLB');
        await dropdownOption.click();

        // verify input contains pes service
        expect(await bsInput.getValue()).toBe('PolicyEnforcementService.GLB');

        // verify input is in bold
        fontWeight = await bsInput.getCSSProperty('font-weight');
        expect(fontWeight.value === 700).toBe(true);

        // submit
        submitButton = await $('button*=Submit');
        await submitButton.click();

        // business service can be seen added to domain
        addBusinessService = await $(
            'a[data-testid="business-service-athenz.dev.functional-test"]'
        );
        await expect(addBusinessService).toHaveTextContaining(
            'PolicyEnforcementService.GLB'
        );

        // click add business service
        await browser.waitUntil(
            async () => await addBusinessService.isClickable()
        );
        await addBusinessService.click();

        // clear current input
        clearInput = await $(
            `.//*[local-name()="svg" and @data-wdio="clear-input"]`
        );
        await browser.waitUntil(async () => await clearInput.isClickable());
        await clearInput.click();

        // submit empty input
        submitButton = await $('button*=Submit');
        await submitButton.click();

        // business service for the domain should be empty
        await browser.waitUntil(
            async () => await addBusinessService.isClickable()
        );
        expect(addBusinessService).toHaveTextContaining('add');
    });

    it('Domain History - modal to change add business service - should preserve input on blur, make input bold when selected in dropdown', async () => {
        await browser.newUser();

        // open domain history page
        await browser.url(`/domain/athenz.dev.functional-test/history`);
        await expect(browser).toHaveUrlContaining('athenz');

        const nonexistentRole = 'nonexistent.role';

        // add random text
        let input = await $('input[name="roles"]');
        await input.addValue(nonexistentRole);

        // blur
        await browser.keys('Tab');

        // input did not change
        expect(await input.getValue()).toBe(nonexistentRole);

        // input is not bold
        let fontWeight = await input.getCSSProperty('font-weight').value;
        expect(fontWeight).toBeUndefined();

        let clearInput = await $(
            `.//*[local-name()="svg" and @data-wdio="clear-input"]`
        );
        await clearInput.click();

        // type valid input and select item in dropdown
        await input.addValue('admin');
        let dropdownOption = await $('div*=admin');
        await dropdownOption.click();

        // verify input contains pes service
        expect(await input.getValue()).toBe('admin');

        // verify input is in bold
        fontWeight = await input.getCSSProperty('font-weight');
        expect(fontWeight.value === 700).toBe(true);
    });

    it('Domain Workflow - input to select dommain - should preserve input on blur, make input bold when selected in dropdown', async () => {
        await browser.newUser();

        // open domain history page
        await browser.url(`/workflow/domain?domain=`);
        await expect(browser).toHaveUrlContaining('athenz');

        const nonexistentDomain = 'nonexistent.domain';

        // add random text
        let input = await $('input[name="domains-inputd"]');
        await input.addValue(nonexistentDomain);

        // blur
        await browser.keys('Tab');

        // input did not change
        expect(await input.getValue()).toBe(nonexistentDomain);

        // input is not bold
        let fontWeight = await input.getCSSProperty('font-weight').value;
        expect(fontWeight).toBeUndefined();

        let clearInput = await $(
            `.//*[local-name()="svg" and @data-wdio="clear-input"]`
        );
        await clearInput.click();

        // type valid input and select item in dropdown
        const testDomain = 'athenz.dev.functional-test';
        await input.addValue(testDomain);
        let dropdownOption = await $(`div*=${testDomain}`);
        await dropdownOption.click();

        // verify input contains pes service
        expect(await input.getValue()).toBe(testDomain);

        // verify input is in bold
        fontWeight = await input.getCSSProperty('font-weight');
        expect(fontWeight.value === 700).toBe(true);
    });
});
