/**
 * @doc.type test
 * @doc.purpose Unit tests for UserStoryParser service (Journey 21.1 - Product Designer Requirement to Wireframe)
 * @doc.layer product
 * @doc.pattern Unit Test
 */

import { describe, it, expect } from 'vitest';
import { UserStoryParser } from '../UserStoryParser';

describe('UserStoryParser', () => {
    describe('parseUserStory', () => {
        it('should parse standard user story format', () => {
            const story = 'As a user, I want to filter products by price, so that I can find affordable items';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.actor).toBe('user');
            expect(result.goal).toBe('filter products by price');
            expect(result.title).toContain('User');
            expect(result.title).toContain('Filter Products By Price');
        });

        it('should extract UI elements from story', () => {
            const story = 'As a user, I want to view the product list and filter by price using a dropdown';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.elements.length).toBeGreaterThan(0);
            const labels = result.elements.map(e => e.label.toLowerCase());
            expect(labels.some(l => l.includes('list') || l.includes('product'))).toBe(true);
        });

        it('should extract business rules with conditions', () => {
            const story = 'As a user, I want to search products. If no results found then show a message.';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.rules.length).toBeGreaterThan(0);
            const rule = result.rules.find(r => r.condition && r.action);
            expect(rule).toBeDefined();
        });

        it('should generate flow steps from elements', () => {
            const story = 'As a user, I want to search for products and view details';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.flow.length).toBe(result.elements.length);
            result.flow.forEach((step, index) => {
                expect(step.order).toBe(index + 1);
            });
        });

        it('should handle stories without standard format', () => {
            const story = 'Users need to see a list of products';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.actor).toBeDefined();
            expect(result.goal).toBeDefined();
            expect(result.elements.length).toBeGreaterThan(0);
        });

        it('should create default screen when no elements found', () => {
            const story = 'As a user, I want to complete the process';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.elements.length).toBeGreaterThan(0);
            expect(result.elements[0].type).toBe('screen');
        });
    });

    describe('validateUserStory', () => {
        it('should validate correct user story', () => {
            const story = 'As a user, I want to filter products by price';
            const result = UserStoryParser.validateUserStory(story);

            expect(result.valid).toBe(true);
            expect(result.errors.length).toBe(0);
        });

        it('should detect empty story', () => {
            const result = UserStoryParser.validateUserStory('');

            expect(result.valid).toBe(false);
            expect(result.errors).toContain('User story cannot be empty');
        });

        it('should warn about missing "As a" format', () => {
            const story = 'I want to filter products';
            const result = UserStoryParser.validateUserStory(story);

            expect(result.warnings.some(w => w.includes('As a'))).toBe(true);
        });

        it('should warn about missing "I want" format', () => {
            const story = 'As a user, filter products by price';
            const result = UserStoryParser.validateUserStory(story);

            expect(result.warnings.some(w => w.includes('I want'))).toBe(true);
        });

        it('should warn about very short stories', () => {
            const story = 'As a user';
            const result = UserStoryParser.validateUserStory(story);

            expect(result.warnings.some(w => w.includes('too short'))).toBe(true);
        });

        it('should warn about very long stories', () => {
            const story = 'As a user, I want to '.repeat(50) + 'do something';
            const result = UserStoryParser.validateUserStory(story);

            expect(result.warnings.some(w => w.includes('quite long'))).toBe(true);
        });
    });

    describe('generateAcceptanceCriteria', () => {
        it('should generate GIVEN-WHEN-THEN criteria', () => {
            const story = 'As a user, I want to search products using a search bar';
            const parsed = UserStoryParser.parseUserStory(story);
            const criteria = UserStoryParser.generateAcceptanceCriteria(parsed);

            expect(criteria.length).toBeGreaterThan(0);
            expect(criteria.some(c => c.includes('GIVEN'))).toBe(true);
        });

        it('should include criteria from elements', () => {
            const story = 'As a user, I want to view a list of products and filter by price';
            const parsed = UserStoryParser.parseUserStory(story);
            const criteria = UserStoryParser.generateAcceptanceCriteria(parsed);

            expect(criteria.length).toBeGreaterThan(0);
        });

        it('should include criteria from rules', () => {
            const story = 'As a user, I want to search products. If no results then show a message.';
            const parsed = UserStoryParser.parseUserStory(story);
            const criteria = UserStoryParser.generateAcceptanceCriteria(parsed);

            expect(criteria.length).toBeGreaterThan(0);
            expect(criteria.some(c => c.includes('THEN'))).toBe(true);
        });

        it('should generate default criteria if none from parsing', () => {
            const parsed = {
                title: 'Test',
                description: 'Test story',
                actor: 'user',
                goal: 'do something',
                elements: [],
                rules: [],
                flow: [],
            };
            const criteria = UserStoryParser.generateAcceptanceCriteria(parsed);

            expect(criteria.length).toBeGreaterThan(0);
        });
    });

    describe('estimateComplexity', () => {
        it('should estimate low complexity for simple stories', () => {
            const story = 'As a user, I want to view a page';
            const parsed = UserStoryParser.parseUserStory(story);
            const result = UserStoryParser.estimateComplexity(parsed);

            expect(result.level).toBe('low');
            expect(result.score).toBeLessThan(10);
        });

        it('should estimate medium complexity for moderate stories', () => {
            const story = 'As a user, I want to view products, filter by price, and sort by name. If out of stock then hide items.';
            const parsed = UserStoryParser.parseUserStory(story);
            const result = UserStoryParser.estimateComplexity(parsed);

            expect(result.level).toMatch(/medium|high/);
            expect(result.score).toBeGreaterThan(10);
        });

        it('should factor in number of elements', () => {
            const story = 'As a user, I want to see a search bar, dropdown, list, filter button, and sort menu';
            const parsed = UserStoryParser.parseUserStory(story);
            const result = UserStoryParser.estimateComplexity(parsed);

            expect(result.factors.some(f => f.includes('UI elements'))).toBe(true);
        });

        it('should factor in business rules', () => {
            const story = 'As a user, I want to search. If no results then show message. When clicked then navigate. Unless logged in cannot proceed.';
            const parsed = UserStoryParser.parseUserStory(story);
            const result = UserStoryParser.estimateComplexity(parsed);

            expect(result.factors.some(f => f.includes('business rules'))).toBe(true);
        });

        it('should factor in flow steps', () => {
            const story = 'As a user, I want to view a list, select item, view details, add to cart, checkout, and confirm order';
            const parsed = UserStoryParser.parseUserStory(story);
            const result = UserStoryParser.estimateComplexity(parsed);

            expect(result.factors.some(f => f.includes('flow steps'))).toBe(true);
        });
    });

    describe('Element Extraction Patterns', () => {
        it('should extract search component', () => {
            const story = 'As a user, I want to search products';
            const result = UserStoryParser.parseUserStory(story);

            const searchElement = result.elements.find(e => e.label.toLowerCase().includes('search'));
            expect(searchElement).toBeDefined();
        });

        it('should extract filter component', () => {
            const story = 'As a user, I want to filter items by category';
            const result = UserStoryParser.parseUserStory(story);

            const filterElement = result.elements.find(e => e.label.toLowerCase().includes('filter'));
            expect(filterElement).toBeDefined();
        });

        it('should extract list component', () => {
            const story = 'As a user, I want to see a list of products';
            const result = UserStoryParser.parseUserStory(story);

            const listElement = result.elements.find(e =>
                e.label.toLowerCase().includes('list') || e.type === 'component'
            );
            expect(listElement).toBeDefined();
        });

        it('should extract button component', () => {
            const story = 'As a user, I want to click a submit button';
            const result = UserStoryParser.parseUserStory(story);

            const buttonElement = result.elements.find(e => e.label.toLowerCase().includes('button'));
            expect(buttonElement).toBeDefined();
        });

        it('should extract form components', () => {
            const story = 'As a user, I want to fill out a form with text fields and checkboxes';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.elements.length).toBeGreaterThan(1);
        });

        it('should avoid duplicate elements', () => {
            const story = 'As a user, I want to search products and search items';
            const result = UserStoryParser.parseUserStory(story);

            const searchElements = result.elements.filter(e => e.label.toLowerCase().includes('search'));
            expect(searchElements.length).toBeLessThanOrEqual(1);
        });
    });

    describe('Business Rule Extraction', () => {
        it('should extract if-then rules', () => {
            const story = 'As a user, I want to search. If no results found then show error message.';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.rules.length).toBeGreaterThan(0);
            const rule = result.rules.find(r => r.description.toLowerCase().includes('if'));
            expect(rule).toBeDefined();
        });

        it('should extract when-then rules', () => {
            const story = 'As a user, when I click submit, validate the form.';
            const result = UserStoryParser.parseUserStory(story);

            const rule = result.rules.find(r => r.description.toLowerCase().includes('when'));
            expect(rule).toBeDefined();
        });

        it('should extract unless rules', () => {
            const story = 'As a user, unless logged in, cannot access dashboard.';
            const result = UserStoryParser.parseUserStory(story);

            const rule = result.rules.find(r => r.description.toLowerCase().includes('unless'));
            expect(rule).toBeDefined();
        });

        it('should extract must/should constraints', () => {
            const story = 'As a user, I must provide email. Password must be 8 characters.';
            const result = UserStoryParser.parseUserStory(story);

            const rules = result.rules.filter(r => r.description.toLowerCase().includes('must'));
            expect(rules.length).toBeGreaterThan(0);
        });

        it('should extract required/mandatory constraints', () => {
            const story = 'As a user, email is required and name is mandatory.';
            const result = UserStoryParser.parseUserStory(story);

            expect(result.rules.length).toBeGreaterThan(0);
        });
    });

    describe('Complex Scenarios', () => {
        it('should handle e-commerce user story', () => {
            const story = `As a customer, I want to browse products, filter by price range, and sort by rating.
            If out of stock then hide item.
            When add to cart then update badge count.
            Must show product image and price.`;

            const result = UserStoryParser.parseUserStory(story);

            expect(result.actor).toBe('customer');
            expect(result.elements.length).toBeGreaterThan(2);
            expect(result.rules.length).toBeGreaterThan(2);
            expect(result.flow.length).toBe(result.elements.length);

            const complexity = UserStoryParser.estimateComplexity(result);
            expect(complexity.level).toMatch(/medium|high|very-high/);
        });

        it('should handle form submission story', () => {
            const story = `As a user, I want to fill a registration form with email and password fields.
            When I click submit, validate all fields.
            If validation fails then show error messages.
            Email must be valid format.
            Password must be at least 8 characters.`;

            const result = UserStoryParser.parseUserStory(story);

            expect(result.elements.some(e => e.label.toLowerCase().includes('form'))).toBe(true);
            expect(result.rules.length).toBeGreaterThan(2);

            const criteria = UserStoryParser.generateAcceptanceCriteria(result);
            expect(criteria.length).toBeGreaterThan(3);
        });

        it('should handle dashboard story', () => {
            const story = `As an admin, I want to view a dashboard with user statistics, charts, and recent activity list.
            When clicking a user then navigate to details page.
            Must load data within 2 seconds.`;

            const result = UserStoryParser.parseUserStory(story);

            expect(result.actor).toBe('admin');
            expect(result.elements.length).toBeGreaterThan(1);

            const complexity = UserStoryParser.estimateComplexity(result);
            expect(complexity.score).toBeGreaterThan(5);
        });
    });
});
