use crate::models::ContentCategory;
use std::collections::HashMap;

/// Content categorizer for usage tracking
/// Classifies apps and websites into educational, social, gaming, etc.
pub struct Categorizer {
    domain_rules: HashMap<String, ContentCategory>,
    app_rules: HashMap<String, ContentCategory>,
}

impl Categorizer {
    /// Create a new categorizer with default rules
    pub fn new() -> Self {
        let mut categorizer = Self {
            domain_rules: HashMap::new(),
            app_rules: HashMap::new(),
        };
        
        categorizer.load_default_rules();
        categorizer
    }

    /// Categorize based on domain (for websites)
    pub fn categorize_domain(&self, domain: &str) -> ContentCategory {
        let domain_lower = domain.to_lowercase();
        
        // Direct domain match
        if let Some(category) = self.domain_rules.get(&domain_lower) {
            return category.clone();
        }
        
        // Check if domain contains known patterns
        for (pattern, category) in &self.domain_rules {
            if domain_lower.contains(pattern) {
                return category.clone();
            }
        }
        
        ContentCategory::Unknown
    }

    /// Categorize based on app name
    pub fn categorize_app(&self, app_name: &str) -> ContentCategory {
        let app_lower = app_name.to_lowercase();
        
        // Direct app name match
        if let Some(category) = self.app_rules.get(&app_lower) {
            return category.clone();
        }
        
        // Check if app name contains known patterns
        for (pattern, category) in &self.app_rules {
            if app_lower.contains(pattern) {
                return category.clone();
            }
        }
        
        ContentCategory::Unknown
    }

    /// Load default categorization rules
    fn load_default_rules(&mut self) {
        // Educational domains
        self.add_domain_rule("khanacademy.org", ContentCategory::Educational);
        self.add_domain_rule("coursera.org", ContentCategory::Educational);
        self.add_domain_rule("udemy.com", ContentCategory::Educational);
        self.add_domain_rule("edx.org", ContentCategory::Educational);
        self.add_domain_rule("codecademy.com", ContentCategory::Educational);
        self.add_domain_rule("duolingo.com", ContentCategory::Educational);
        self.add_domain_rule("wikipedia.org", ContentCategory::Educational);
        self.add_domain_rule("stackoverflow.com", ContentCategory::Educational);
        self.add_domain_rule("github.com", ContentCategory::Educational);
        self.add_domain_rule("medium.com", ContentCategory::Educational);
        
        // Social media domains
        self.add_domain_rule("facebook.com", ContentCategory::Social);
        self.add_domain_rule("twitter.com", ContentCategory::Social);
        self.add_domain_rule("instagram.com", ContentCategory::Social);
        self.add_domain_rule("tiktok.com", ContentCategory::Social);
        self.add_domain_rule("snapchat.com", ContentCategory::Social);
        self.add_domain_rule("linkedin.com", ContentCategory::Social);
        self.add_domain_rule("reddit.com", ContentCategory::Social);
        self.add_domain_rule("pinterest.com", ContentCategory::Social);
        
        // Gaming domains
        self.add_domain_rule("twitch.tv", ContentCategory::Gaming);
        self.add_domain_rule("steam", ContentCategory::Gaming);
        self.add_domain_rule("epicgames.com", ContentCategory::Gaming);
        self.add_domain_rule("roblox.com", ContentCategory::Gaming);
        self.add_domain_rule("minecraft.net", ContentCategory::Gaming);
        self.add_domain_rule("fortnite.com", ContentCategory::Gaming);
        
        // Entertainment domains
        self.add_domain_rule("youtube.com", ContentCategory::Entertainment);
        self.add_domain_rule("netflix.com", ContentCategory::Entertainment);
        self.add_domain_rule("hulu.com", ContentCategory::Entertainment);
        self.add_domain_rule("disneyplus.com", ContentCategory::Entertainment);
        self.add_domain_rule("spotify.com", ContentCategory::Entertainment);
        self.add_domain_rule("soundcloud.com", ContentCategory::Entertainment);
        self.add_domain_rule("twitch.tv", ContentCategory::Entertainment);
        
        // Productivity domains
        self.add_domain_rule("google.com/docs", ContentCategory::Productivity);
        self.add_domain_rule("google.com/sheets", ContentCategory::Productivity);
        self.add_domain_rule("google.com/slides", ContentCategory::Productivity);
        self.add_domain_rule("office.com", ContentCategory::Productivity);
        self.add_domain_rule("notion.so", ContentCategory::Productivity);
        self.add_domain_rule("trello.com", ContentCategory::Productivity);
        self.add_domain_rule("asana.com", ContentCategory::Productivity);
        self.add_domain_rule("slack.com", ContentCategory::Productivity);
        
        // Shopping domains
        self.add_domain_rule("amazon.com", ContentCategory::Shopping);
        self.add_domain_rule("ebay.com", ContentCategory::Shopping);
        self.add_domain_rule("walmart.com", ContentCategory::Shopping);
        self.add_domain_rule("target.com", ContentCategory::Shopping);
        self.add_domain_rule("etsy.com", ContentCategory::Shopping);
        
        // News domains
        self.add_domain_rule("cnn.com", ContentCategory::News);
        self.add_domain_rule("bbc.com", ContentCategory::News);
        self.add_domain_rule("nytimes.com", ContentCategory::News);
        self.add_domain_rule("theguardian.com", ContentCategory::News);
        self.add_domain_rule("reuters.com", ContentCategory::News);
        
        // Communication domains
        self.add_domain_rule("gmail.com", ContentCategory::Communication);
        self.add_domain_rule("outlook.com", ContentCategory::Communication);
        self.add_domain_rule("zoom.us", ContentCategory::Communication);
        self.add_domain_rule("teams.microsoft.com", ContentCategory::Communication);
        self.add_domain_rule("discord.com", ContentCategory::Communication);
        self.add_domain_rule("whatsapp.com", ContentCategory::Communication);
        
        // Educational apps
        self.add_app_rule("khan academy", ContentCategory::Educational);
        self.add_app_rule("duolingo", ContentCategory::Educational);
        
        // Social apps
        self.add_app_rule("facebook", ContentCategory::Social);
        self.add_app_rule("instagram", ContentCategory::Social);
        self.add_app_rule("twitter", ContentCategory::Social);
        self.add_app_rule("tiktok", ContentCategory::Social);
        self.add_app_rule("snapchat", ContentCategory::Social);
        self.add_app_rule("discord", ContentCategory::Communication);
        
        // Gaming apps
        self.add_app_rule("steam", ContentCategory::Gaming);
        self.add_app_rule("minecraft", ContentCategory::Gaming);
        self.add_app_rule("roblox", ContentCategory::Gaming);
        self.add_app_rule("fortnite", ContentCategory::Gaming);
        self.add_app_rule("league of legends", ContentCategory::Gaming);
        
        // Entertainment apps
        self.add_app_rule("spotify", ContentCategory::Entertainment);
        self.add_app_rule("netflix", ContentCategory::Entertainment);
        self.add_app_rule("youtube", ContentCategory::Entertainment);
        
        // Productivity apps
        self.add_app_rule("word", ContentCategory::Productivity);
        self.add_app_rule("excel", ContentCategory::Productivity);
        self.add_app_rule("powerpoint", ContentCategory::Productivity);
        self.add_app_rule("outlook", ContentCategory::Communication);
        self.add_app_rule("teams", ContentCategory::Communication);
        self.add_app_rule("zoom", ContentCategory::Communication);
        self.add_app_rule("slack", ContentCategory::Communication);
        self.add_app_rule("notion", ContentCategory::Productivity);
        self.add_app_rule("trello", ContentCategory::Productivity);
        self.add_app_rule("visual studio code", ContentCategory::Productivity);
        self.add_app_rule("vscode", ContentCategory::Productivity);
        self.add_app_rule("intellij", ContentCategory::Productivity);
        self.add_app_rule("pycharm", ContentCategory::Productivity);
        self.add_app_rule("sublime", ContentCategory::Productivity);
        
        // Browsers (default to Unknown, will be categorized by URL)
        self.add_app_rule("chrome", ContentCategory::Unknown);
        self.add_app_rule("firefox", ContentCategory::Unknown);
        self.add_app_rule("safari", ContentCategory::Unknown);
        self.add_app_rule("edge", ContentCategory::Unknown);
    }

    /// Add a domain categorization rule
    pub fn add_domain_rule(&mut self, domain: &str, category: ContentCategory) {
        self.domain_rules.insert(domain.to_lowercase(), category);
    }

    /// Add an app categorization rule
    pub fn add_app_rule(&mut self, app_name: &str, category: ContentCategory) {
        self.app_rules.insert(app_name.to_lowercase(), category);
    }
}

impl Default for Categorizer {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_categorize_educational_domain() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_domain("khanacademy.org"),
            ContentCategory::Educational
        );
        assert_eq!(
            categorizer.categorize_domain("stackoverflow.com"),
            ContentCategory::Educational
        );
    }

    #[test]
    fn test_categorize_social_domain() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_domain("facebook.com"),
            ContentCategory::Social
        );
        assert_eq!(
            categorizer.categorize_domain("instagram.com"),
            ContentCategory::Social
        );
    }

    #[test]
    fn test_categorize_gaming_domain() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_domain("roblox.com"),
            ContentCategory::Gaming
        );
        assert_eq!(
            categorizer.categorize_domain("minecraft.net"),
            ContentCategory::Gaming
        );
    }

    #[test]
    fn test_categorize_entertainment_domain() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_domain("youtube.com"),
            ContentCategory::Entertainment
        );
        assert_eq!(
            categorizer.categorize_domain("netflix.com"),
            ContentCategory::Entertainment
        );
    }

    #[test]
    fn test_categorize_productivity_app() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_app("Visual Studio Code"),
            ContentCategory::Productivity
        );
        assert_eq!(
            categorizer.categorize_app("Microsoft Word"),
            ContentCategory::Productivity
        );
    }

    #[test]
    fn test_categorize_gaming_app() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_app("Minecraft"),
            ContentCategory::Gaming
        );
        assert_eq!(
            categorizer.categorize_app("Steam"),
            ContentCategory::Gaming
        );
    }

    #[test]
    fn test_categorize_unknown() {
        let categorizer = Categorizer::new();
        assert_eq!(
            categorizer.categorize_domain("unknown-website.com"),
            ContentCategory::Unknown
        );
        assert_eq!(
            categorizer.categorize_app("Unknown App"),
            ContentCategory::Unknown
        );
    }

    #[test]
    fn test_custom_rules() {
        let mut categorizer = Categorizer::new();
        categorizer.add_domain_rule("custom-edu.com", ContentCategory::Educational);
        categorizer.add_app_rule("custom app", ContentCategory::Gaming);

        assert_eq!(
            categorizer.categorize_domain("custom-edu.com"),
            ContentCategory::Educational
        );
        assert_eq!(
            categorizer.categorize_app("Custom App"),
            ContentCategory::Gaming
        );
    }
}
