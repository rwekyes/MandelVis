# Contributing to MandelVis

Thanks for your interest! Contributions of all kinds are welcome — bug reports, feature ideas, and code.

## Reporting a Bug

Open an [issue](https://github.com/rwekyes/MandelVis/issues) and include:
- What you expected to happen
- What actually happened
- Steps to reproduce it
- Your Java version (`java -version`) and OS

## Suggesting a Feature

Open an [issue](https://github.com/rwekyes/MandelVis/issues) describing what you'd like and why it would be useful. No formal template required — just enough context to have a conversation.

## Making a Code Contribution

1. Fork the repo and clone your fork
2. Create a branch:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/what-youre-fixing
   ```
3. Make your changes
4. Push your branch and open a pull request against `master`

## Code Style

- Standard Java indentation (4 spaces)
- Remove dead code and commented-out blocks before submitting
- Keep methods focused — if a method is doing two things, it probably should be two methods

## Commit Messages

Use short, imperative present-tense subject lines:

```
Add scroll zoom centered on cursor
Fix gradient preview not updating on stop removal
```

Not `"fixed bug"` or `"some changes"`. A good commit message tells the story of the project to anyone reading the history later.
