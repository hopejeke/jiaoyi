# PowerShell script to remove sensitive Stripe keys from git history
# This script uses git filter-branch to rewrite history

$env:FILTER_BRANCH_SQUELCH_WARNING = 1

# Remove sensitive keys from application.properties
git filter-branch -f --tree-filter '
if [ -f order-service/src/main/resources/application.properties ]; then
  sed -i "s/stripe\.secret-key=sk_test_[A-Za-z0-9]*/stripe.secret-key=\${STRIPE_SECRET_KEY:}/g" order-service/src/main/resources/application.properties
  sed -i "s/stripe\.publishable-key=pk_test_[A-Za-z0-9]*/stripe.publishable-key=\${STRIPE_PUBLISHABLE_KEY:}/g" order-service/src/main/resources/application.properties
fi
if [ -f order-service/src/main/resources/application-local.properties.example ]; then
  sed -i "/#stripe\.secret-key=sk_test_/d" order-service/src/main/resources/application-local.properties.example
  sed -i "/#stripe\.publishable-key=pk_test_/d" order-service/src/main/resources/application-local.properties.example
fi
' --prune-empty --tag-name-filter cat -- --all



