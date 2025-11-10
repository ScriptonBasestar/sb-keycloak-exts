/**
 * Corporate Clean Theme - Login JavaScript
 * Keycloak Extensions Project
 */

(function() {
    'use strict';

    // Form validation helper
    function validateForm() {
        const username = document.getElementById('username');
        const password = document.getElementById('password');

        if (!username || !password) return true;

        let isValid = true;

        if (username.value.trim() === '') {
            username.classList.add('input-error');
            isValid = false;
        } else {
            username.classList.remove('input-error');
        }

        if (password.value === '') {
            password.classList.add('input-error');
            isValid = false;
        } else {
            password.classList.remove('input-error');
        }

        return isValid;
    }

    // Real-time validation
    document.addEventListener('DOMContentLoaded', function() {
        const username = document.getElementById('username');
        const password = document.getElementById('password');

        if (username) {
            username.addEventListener('input', function() {
                if (this.value.trim() !== '') {
                    this.classList.remove('input-error');
                }
            });
        }

        if (password) {
            password.addEventListener('input', function() {
                if (this.value !== '') {
                    this.classList.remove('input-error');
                }
            });
        }

        // Form submit validation
        const loginForm = document.getElementById('kc-form-login');
        if (loginForm) {
            loginForm.addEventListener('submit', function(e) {
                if (!validateForm()) {
                    e.preventDefault();
                    return false;
                }
            });
        }

        // Auto-focus first empty field
        if (username && username.value.trim() === '') {
            username.focus();
        } else if (password && password.value === '') {
            password.focus();
        }
    });

    // Locale dropdown toggle
    const localeButton = document.getElementById('kc-current-locale-link');
    if (localeButton) {
        localeButton.addEventListener('click', function(e) {
            e.preventDefault();
            const dropdown = this.nextElementSibling;
            if (dropdown) {
                dropdown.style.display = dropdown.style.display === 'block' ? 'none' : 'block';
            }
        });

        // Close dropdown when clicking outside
        document.addEventListener('click', function(e) {
            if (!e.target.matches('#kc-current-locale-link')) {
                const dropdowns = document.querySelectorAll('#kc-locale-dropdown ul');
                dropdowns.forEach(function(dropdown) {
                    dropdown.style.display = 'none';
                });
            }
        });
    }

    // Accessibility: ESC key to close dropdowns
    document.addEventListener('keydown', function(e) {
        if (e.key === 'Escape') {
            const dropdowns = document.querySelectorAll('#kc-locale-dropdown ul');
            dropdowns.forEach(function(dropdown) {
                dropdown.style.display = 'none';
            });
        }
    });

    console.log('Corporate Clean Theme loaded');
})();
